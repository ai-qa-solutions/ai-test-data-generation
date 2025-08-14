package github.ai.qa.solutions.nodes;

import static github.ai.qa.solutions.state.AgentState.StateKey.JSON_SCHEMA;
import static github.ai.qa.solutions.state.AgentState.StateKey.SCHEMA_VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.ai.qa.solutions.services.ChatClientRouter;
import github.ai.qa.solutions.state.AgentState;
import github.ai.qa.solutions.tools.ValidateJsonSchemaTool;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ValidateJsonSchemaNode implements NodeAction<AgentState> {
    private static final Logger log = LoggerFactory.getLogger(ValidateJsonSchemaNode.class);
    private final ChatClientRouter router;
    private final ValidateJsonSchemaTool validateJsonSchemaTool;
    private final ObjectMapper objectMapper;

    @Override
    @SneakyThrows
    public Map<String, Object> apply(final AgentState state) {
        log.info("▶️ Stage: ValidateJsonSchemaNode — starting");
        final String schema = state.get(JSON_SCHEMA);

        String content = null;
        try {
            content = router.forNode("ValidateJsonSchemaNode")
                    .prompt(
                            """
                            Validate the following JSON Schema strictly by calling the tool `validateJsonSchema`.
                            Return ONLY the tool JSON: {\"ok\":true,\"compactSchema\":\"...\"} or {\"ok\":false,\"error\":\"...\"}.

                            JSON Schema:
                            %s
                            """
                                    .formatted(schema))
                    .system(
                            """
                            You MUST call the tool `validateJsonSchema` to validate and compact the schema.
                            Then respond ONLY with the tool's JSON response, no extra text, no markdown.
                            """)
                    .tools(validateJsonSchemaTool)
                    .call()
                    .content();
        } catch (Exception ignored) {
            // ignore and fallback to direct tool call
        }

        if (content == null || content.isBlank()) {
            content = validateJsonSchemaTool.validateAndCompactSchema(schema);
        } else if (content.startsWith("```")) {
            content = content.replaceAll("^```[a-zA-Z]*\\n|```$", "").trim();
        }

        try {
            final JsonNode root = objectMapper.readTree(content);
            final boolean ok = root.path("ok").asBoolean(false);
            if (!ok) {
                final String error = root.path("error").asText("Unknown error");
                throw new GraphStateException("Incorrect jsonSchema:" + error);
            }
            final String compact = root.path("compactSchema").asText();
            final String version = root.path("version").asText("");
            if (version == null || version.isEmpty()) {
                return Map.of(JSON_SCHEMA.name(), compact);
            }
            return Map.of(JSON_SCHEMA.name(), compact, SCHEMA_VERSION.name(), version);
        } catch (GraphStateException e) {
            throw e;
        } catch (Exception e) {
            final String fallback = validateJsonSchemaTool.validateAndCompactSchema(schema);
            try {
                final JsonNode root2 = objectMapper.readTree(fallback);
                final boolean ok2 = root2.path("ok").asBoolean(false);
                if (!ok2) {
                    final String error = root2.path("error").asText("Unknown error");
                    throw new GraphStateException("Incorrect jsonSchema:" + error);
                }
                final String compact2 = root2.path("compactSchema").asText();
                final String version2 = root2.path("version").asText("");
                if (version2 == null || version2.isEmpty()) {
                    return Map.of(JSON_SCHEMA.name(), compact2);
                }
                return Map.of(JSON_SCHEMA.name(), compact2, SCHEMA_VERSION.name(), version2);
            } catch (Exception ex) {
                throw new GraphStateException("Incorrect jsonSchema: cannot parse tool response");
            }
        }
    }
}
