package github.ai.qa.solutions.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import github.ai.qa.solutions.configuration.AiClientsConfiguration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;

class NodeModelChatClientRouterTest {

    @Test
    @DisplayName("Decision: configured GigaChat model")
    void decideConfiguredGiga() {
        Map<String, String> cfg = Map.of("X", "GigaChat-2-Max");
        var d = NodeModelChatClientRouter.decideFamily("X", cfg);
        assertEquals("GigaChat", d.family);
        assertEquals("GigaChat-2-Max", d.modelLabel);
    }

    @Test
    @DisplayName("Decision: configured OpenRouter model")
    void decideConfiguredOpenRouter() {
        Map<String, String> cfg = Map.of("X", "deepseek/deepseek-r1");
        var d = NodeModelChatClientRouter.decideFamily("X", cfg);
        assertEquals("OpenRouter", d.family);
        assertEquals("deepseek/deepseek-r1", d.modelLabel);
    }

    @Test
    @DisplayName("Decision: defaults and heuristics")
    void decideDefaultsAndHeuristics() {
        // Default sets
        var d1 = NodeModelChatClientRouter.decideFamily("GenerateJsonNode", Collections.emptyMap());
        assertEquals("GigaChat", d1.family);
        assertEquals("<default>", d1.modelLabel);

        var d2 = NodeModelChatClientRouter.decideFamily("ValidateJsonSchemaNode", Collections.emptyMap());
        assertEquals("OpenRouter", d2.family);
        assertEquals("<default>", d2.modelLabel);

        // Heuristic by name
        var d3 = NodeModelChatClientRouter.decideFamily("SomeValidateThing", Collections.emptyMap());
        assertEquals("OpenRouter", d3.family);
        assertEquals("<heuristic>", d3.modelLabel);

        var d4 = NodeModelChatClientRouter.decideFamily("ProduceData", Collections.emptyMap());
        assertEquals("GigaChat", d4.family);
        assertEquals("<heuristic>", d4.modelLabel);
    }

    @Test
    @DisplayName("forNode: throws when no ChatClient available")
    void forNodeThrowsWhenNoClients() {
        ObjectProvider<ChatClient> nullProvider = new ObjectProvider<>() {
            @Override
            public ChatClient getObject(Object... args) {
                return null;
            }

            @Override
            public ChatClient getIfAvailable() {
                return null;
            }

            @Override
            public ChatClient getIfUnique() {
                return null;
            }

            @Override
            public ChatClient getObject() {
                return null;
            }
        };
        var props = new AiClientsConfiguration.NodeModelRoutingProperties(new HashMap<>());
        var router = new NodeModelChatClientRouter(nullProvider, nullProvider, props);
        assertThrows(IllegalStateException.class, () -> router.forNode("AnyNode"));
    }
}
