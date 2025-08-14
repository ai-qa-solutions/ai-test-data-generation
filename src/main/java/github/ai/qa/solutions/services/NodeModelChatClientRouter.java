package github.ai.qa.solutions.services;

import github.ai.qa.solutions.configuration.AiClientsConfiguration;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Routes a node/tool name to a {@link ChatClient} family (GigaChat or OpenRouter).
 *
 * <p>Resolution order:</p>
 * - explicit model in properties → family inferred from model string
 * - default lists per role → family by curated defaults
 * - heuristic by name → OpenRouter for validate/think/reason, otherwise GigaChat
 *
 * <p>For testability, the pure decision is exposed by {@link #decideFamily(String, Map)}.</p>
 */
@Service
public class NodeModelChatClientRouter implements ChatClientRouter {
    private static final Logger log = LoggerFactory.getLogger(NodeModelChatClientRouter.class);
    private final ObjectProvider<ChatClient> gigaChatClient;
    private final ObjectProvider<ChatClient> openRouterClient;
    private final AiClientsConfiguration.NodeModelRoutingProperties props;

    static final Set<String> DEFAULT_OPENROUTER = Set.of(
            // Nodes
            "ValidateJsonSchemaNode",
            "VerifyJsonByJsonSchemaNode",
            "ReasonAndRouteNode",
            // Tools
            "ThinkHowToGenerateTool",
            "ThinkHowToFixJsonTool");

    static final Set<String> DEFAULT_GIGACHAT = Set.of(
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

    /** Minimal holder for a routing decision. */
    static final class Decision {
        final String family; // "GigaChat" or "OpenRouter"
        final String modelLabel; // configured model, or <default>/<heuristic>

        Decision(final String family, final String modelLabel) {
            this.family = family;
            this.modelLabel = modelLabel;
        }
    }

    /**
     * Pure decision logic that chooses target family and model label.
     */
    static Decision decideFamily(final String nodeOrToolSimpleName, final Map<String, String> configured) {
        final String model = configured == null ? null : configured.get(nodeOrToolSimpleName);
        if (model != null && !model.isBlank()) {
            if (isGigaChatModel(model)) return new Decision("GigaChat", model);
            if (isOpenRouterModel(model)) return new Decision("OpenRouter", model);
        }
        if (DEFAULT_GIGACHAT.contains(nodeOrToolSimpleName)) return new Decision("GigaChat", "<default>");
        if (DEFAULT_OPENROUTER.contains(nodeOrToolSimpleName)) return new Decision("OpenRouter", "<default>");
        final String nodeLow = nodeOrToolSimpleName.toLowerCase();
        if (nodeLow.contains("validate") || nodeLow.contains("think") || nodeLow.contains("reason")) {
            return new Decision("OpenRouter", "<heuristic>");
        }
        return new Decision("GigaChat", "<heuristic>");
    }

    @Override
    public ChatClient forNode(final String nodeOrToolSimpleName) {
        final Decision d = decideFamily(nodeOrToolSimpleName, props.nodes());
        if ("GigaChat".equals(d.family)) {
            return pickAndLog(nodeOrToolSimpleName, d.family, d.modelLabel, gigaChatClient, openRouterClient);
        }
        return pickAndLog(nodeOrToolSimpleName, d.family, d.modelLabel, openRouterClient, gigaChatClient);
    }

    private static boolean isGigaChatModel(final String model) {
        final String m = model.toLowerCase();
        return m.contains("gigachat") || m.startsWith("giga");
    }

    private static boolean isOpenRouterModel(final String model) {
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
