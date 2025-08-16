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
    /** Logs routing decisions. */
    private static final Logger log = LoggerFactory.getLogger(NodeModelChatClientRouter.class);
    /** Provider for GigaChat-backed {@link ChatClient} (generative/produce JSON). */
    private final ObjectProvider<ChatClient> gigaChatClient;
    /** Provider for OpenRouter-backed {@link ChatClient} (validate/think/reason). */
    private final ObjectProvider<ChatClient> openRouterClient;
    /** Node/tool → model mapping from external configuration. */
    private final AiClientsConfiguration.NodeModelRoutingProperties props;

    /** Node/tool simple names routed to OpenRouter by default. */
    static final Set<String> DEFAULT_OPENROUTER = Set.of(
            // Nodes
            "ValidateJsonSchemaNode",
            "VerifyJsonByJsonSchemaNode",
            "ReasonAndRouteNode",
            // Tools
            "ThinkHowToGenerateTool",
            "ThinkHowToFixJsonTool");

    /** Node/tool simple names routed to GigaChat by default. */
    static final Set<String> DEFAULT_GIGACHAT = Set.of(
            // Nodes
            "GenerateJsonNode", "FixErrorsInJsonNode",
            // Tools
            "GenerateJsonBySchemaTool", "FixValidationErrorsInJsonTool");

    /**
     * Creates a router with two families and external routing properties.
     *
     * @param gigaChatClient   provider for GigaChat chat client
     * @param openRouterClient provider for OpenRouter chat client
     * @param props            configured node→model overrides
     */
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
        /** Selected family: "GigaChat" or "OpenRouter". */
        final String family;
        /** Model label: explicit model or one of {@code <default>} / {@code <heuristic>}. */
        final String modelLabel;

        Decision(final String family, final String modelLabel) {
            this.family = family;
            this.modelLabel = modelLabel;
        }
    }

    /**
     * Pure decision logic that chooses target family and model label.
     *
     * @param nodeOrToolSimpleName simple class name of node/tool
     * @param configured optional overrides mapping simple name to model string
     * @return routing decision including family and label
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

    /**
     * Resolves the {@link ChatClient} for the given node/tool simple name.
     *
     * @param nodeOrToolSimpleName simple class name of node/tool
     * @return a non-null {@link ChatClient}
     * @throws IllegalStateException when neither family is available
     */
    @Override
    public ChatClient forNode(final String nodeOrToolSimpleName) {
        final Decision d = decideFamily(nodeOrToolSimpleName, props.nodes());
        if ("GigaChat".equals(d.family)) {
            return pickAndLog(nodeOrToolSimpleName, d.family, d.modelLabel, gigaChatClient, openRouterClient);
        }
        return pickAndLog(nodeOrToolSimpleName, d.family, d.modelLabel, openRouterClient, gigaChatClient);
    }

    /**
     * Heuristic: whether model string denotes a GigaChat model.
     *
     * @param model model identifier string
     * @return true when likely a GigaChat model
     */
    private static boolean isGigaChatModel(final String model) {
        final String m = model.toLowerCase();
        return m.contains("gigachat") || m.startsWith("giga");
    }

    /**
     * Heuristic: whether model string denotes an OpenRouter model.
     *
     * @param model model identifier string
     * @return true when likely an OpenRouter model
     */
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
