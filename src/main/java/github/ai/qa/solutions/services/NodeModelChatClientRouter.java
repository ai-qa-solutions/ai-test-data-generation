package github.ai.qa.solutions.services;

import github.ai.qa.solutions.configuration.AiClientsConfiguration;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NodeModelChatClientRouter implements ChatClientRouter {
    private final ObjectProvider<ChatClient> gigaChatClient;
    private final ObjectProvider<ChatClient> openRouterClient;
    private final AiClientsConfiguration.NodeModelRoutingProperties props;

    private static final Set<String> DEFAULT_OPENROUTER = Set.of(
            // Nodes
            "ValidateJsonSchemaNode",
            "VerifyJsonByJsonSchemaNode",
            "ReasonAndRouteNode",
            // Tools
            "ThinkHowToGenerateTool",
            "ThinkHowToFixJsonTool");

    private static final Set<String> DEFAULT_GIGACHAT = Set.of(
            // Nodes
            "GenerateJsonNode", "FixErrorsInJsonNode",
            // Tools
            "GenerateJsonBySchemaTool", "FixValidationErrorsInJsonTool");

    public NodeModelChatClientRouter(
            @Qualifier("generativeChatClient") final ObjectProvider<ChatClient> gigaChatClient,
            @Qualifier("thinkingChatClient") final ObjectProvider<ChatClient> openRouterClient,
            final AiClientsConfiguration.NodeModelRoutingProperties props) {
        this.gigaChatClient = gigaChatClient;
        this.openRouterClient = openRouterClient;
        this.props = props;
    }

    @Override
    public ChatClient forNode(final String nodeOrToolSimpleName) {
        final Map<String, String> configured = props.getNodes();
        final String model = configured == null ? null : configured.get(nodeOrToolSimpleName);

        if (model != null && !model.isBlank()) {
            if (isGigaChatModel(model)) {
                return pickAndLog(nodeOrToolSimpleName, "GigaChat", model, gigaChatClient, openRouterClient);
            }
            if (isOpenRouterModel(model)) {
                return pickAndLog(
                        nodeOrToolSimpleName, "OpenRouter", model, openRouterClient, gigaChatClient);
            }
        }

        // Fallback to sensible defaults by role
        if (DEFAULT_GIGACHAT.contains(nodeOrToolSimpleName)) {
            return pickAndLog(
                    nodeOrToolSimpleName, "GigaChat", "<default>", gigaChatClient, openRouterClient);
        }

        if (DEFAULT_OPENROUTER.contains(nodeOrToolSimpleName)) {
            return pickAndLog(
                    nodeOrToolSimpleName, "OpenRouter", "<default>", openRouterClient, gigaChatClient);
        }

        // Heuristic: validate/think/reason → OpenRouter, else → GigaChat
        final String nodeLow = nodeOrToolSimpleName.toLowerCase();
        if (nodeLow.contains("validate") || nodeLow.contains("think") || nodeLow.contains("reason")) {
            return pickAndLog(
                    nodeOrToolSimpleName, "OpenRouter", "<heuristic>", openRouterClient, gigaChatClient);
        }

        return pickAndLog(nodeOrToolSimpleName, "GigaChat", "<heuristic>", gigaChatClient, openRouterClient);
    }

    private boolean isGigaChatModel(final String model) {
        final String m = model.toLowerCase();
        return m.contains("gigachat") || m.startsWith("giga");
    }

    private boolean isOpenRouterModel(final String model) {
        // OpenRouter models typically include provider prefix like provider/model
        final String m = model.toLowerCase();
        return m.contains("/") || m.contains(":");
    }

    private ChatClient pickAndLog(
            final String nodeName,
            final String preferredFamily,
            final String modelLabel,
            final ObjectProvider<ChatClient> preferred,
            final ObjectProvider<ChatClient> fallback) {
        ChatClient chatClient = preferred.getIfAvailable();
        String family;
        if (chatClient != null) {
            family = preferredFamily;
        } else {
            chatClient = fallback.getIfAvailable();
            family = preferredFamily.equals("GigaChat") ? "OpenRouter" : "GigaChat";
        }
        if (chatClient == null) {
            throw new IllegalStateException(
                    "No ChatClient beans available for routing. Ensure profiles are configured.");
        }
        log.info("🎯 Route [{}] → family={} model={}", nodeName, family, modelLabel);
        return chatClient;
    }
}
