package github.ai.qa.solutions.nodes;

import static github.ai.qa.solutions.state.AgentState.StateKey.JSON_SCHEMA;
import static github.ai.qa.solutions.state.AgentState.StateKey.SCHEMA_VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.ai.qa.solutions.services.ChatClientRouter;
import github.ai.qa.solutions.state.AgentState;
import github.ai.qa.solutions.tools.ValidateJsonSchemaTool;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.NonNull;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Validates and compacts the input JSON Schema.
 *
 * <p>Flow: ask the model to call the `validateJsonSchema` tool; if output is missing/invalid,
 * fall back to a direct tool call. Produces a compacted schema string and optional detected version.
 */
@Service
public class ValidateJsonSchemaNode implements NodeAction<AgentState> {
    /** Logs node lifecycle. */
    private static final Logger log = LoggerFactory.getLogger(ValidateJsonSchemaNode.class);
    /** Router that selects the appropriate chat client for this node. */
    private final ChatClientRouter router;
    /** Tool that validates and compacts JSON Schema locally. */
    private final ValidateJsonSchemaTool validateJsonSchemaTool;
    /** JSON parser for tool/model responses. */
    private final ObjectMapper objectMapper;

    /**
     * Creates the node with required collaborators.
     *
     * @param router chat client router for LLM-assisted validation
     * @param validateJsonSchemaTool local validation tool
     * @param objectMapper JSON parser
     */
    public ValidateJsonSchemaNode(
            final ChatClientRouter router,
            final ValidateJsonSchemaTool validateJsonSchemaTool,
            final ObjectMapper objectMapper) {
        this.router = Objects.requireNonNull(router, "router");
        this.validateJsonSchemaTool = Objects.requireNonNull(validateJsonSchemaTool, "validateJsonSchemaTool");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /** Prompt template to instruct the model to call the validator tool. */
    private static final String PROMPT_TEMPLATE =
            """
            Validate the following JSON Schema strictly by calling the tool `validateJsonSchema`.
            Return ONLY the tool JSON: {\"ok\":true,\"compactSchema\":\"...\"} or {\"ok\":false,\"error\":\"...\"}.

            JSON Schema:
            %s
            """;

    /** System instruction to enforce tool-call-only behavior. */
    private static final String SYSTEM_INSTRUCTION =
            """
            You MUST call the tool `validateJsonSchema` to validate and compact the schema.
            Then respond ONLY with the tool's JSON response, no extra text, no markdown.
            """;

    @Override
    public Map<String, Object> apply(@NonNull final AgentState state) {
        log.info("▶️ Stage: ValidateJsonSchemaNode — starting");
        final String schema = state.get(JSON_SCHEMA);

        String content;
        try {
            content = router.forNode(getClass().getSimpleName())
                    .prompt(PROMPT_TEMPLATE.formatted(schema))
                    .system(SYSTEM_INSTRUCTION)
                    .tools(validateJsonSchemaTool)
                    .call()
                    .content();
        } catch (RuntimeException ignored) {
            content = null;
        }

        if (content == null || content.isBlank()) {
            content = validateJsonSchemaTool.validateAndCompactSchema(schema);
        } else if (content.startsWith("```")) {
            content = stripFences(content);
        }

        final Optional<Map<String, Object>> parsed = parse(content);
        if (parsed.isPresent()) return parsed.get();

        // Fallback: re-run tool locally and parse; else error
        final String fallback = validateJsonSchemaTool.validateAndCompactSchema(schema);
        final Optional<Map<String, Object>> parsedFallback = parse(fallback);
        return parsedFallback.orElseThrow(
                () -> new IllegalStateException("Incorrect jsonSchema: cannot parse tool response"));
    }

    /**
     * Removes Markdown code fences if present.
     *
     * @param s possibly fenced text
     * @return text without surrounding fences
     */
    private String stripFences(@NonNull final String s) {
        if (!s.startsWith("```")) return s;
        return s.replaceAll("^```[a-zA-Z]*\\n|```$", "").trim();
    }

    /**
     * Parses the validator response and returns either compact schema + optional version,
     * or empty when the content is not in the expected shape.
     *
     * @param content tool or model JSON output
     * @return parsed map when valid; otherwise empty
     */
    private Optional<Map<String, Object>> parse(final String content) {
        if (content == null || content.isBlank()) return Optional.empty();
        try {
            final JsonNode root = objectMapper.readTree(content);
            final boolean ok = root.path("ok").asBoolean(false);
            if (!ok) return Optional.empty();

            final String compact = root.path("compactSchema").asText("");
            final String version = root.path("version").asText("");
            if (compact.isEmpty()) return Optional.empty();
            if (version.isEmpty()) {
                return Optional.of(Map.of(JSON_SCHEMA.name(), compact));
            }
            return Optional.of(Map.of(JSON_SCHEMA.name(), compact, SCHEMA_VERSION.name(), version));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }
}
