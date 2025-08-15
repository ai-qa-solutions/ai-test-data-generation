package github.ai.qa.solutions.nodes;

import static github.ai.qa.solutions.state.AgentState.StateKey.GENERATED_JSON;
import static github.ai.qa.solutions.state.AgentState.StateKey.JSON_SCHEMA;
import static github.ai.qa.solutions.state.AgentState.StateKey.VALIDATION_RESULT;
import static github.ai.qa.solutions.state.AgentState.StateKey.VALIDATION_SIGNATURE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.ai.qa.solutions.services.ChatClientRouter;
import github.ai.qa.solutions.state.AgentState;
import github.ai.qa.solutions.tools.ValidateJsonBySchemaTool;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.NonNull;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Validates generated JSON against a JSON Schema by asking the model to call the validation tool
 * and falling back to a direct tool call when needed. Produces a stable, compact validation result
 * and a sortable signature of errors for routing and deduplication.
 */
@Service
public class VerifyJsonByJsonSchemaNode implements NodeAction<AgentState> {
    /** Logs node lifecycle. */
    private static final Logger log = LoggerFactory.getLogger(VerifyJsonByJsonSchemaNode.class);
    /**
     * Tool that performs strict JSON-vs-Schema validation locally (no LLM).
     */
    private final ValidateJsonBySchemaTool validateJsonBySchemaTool;

    /**
     * Router that selects the appropriate chat client for this node (LLM path).
     */
    private final ChatClientRouter router;

    /**
     * JSON parser used to parse tool/LLM responses.
     */
    private final ObjectMapper objectMapper;

    /**
     * Creates the node with required collaborators.
     *
     * @param validateJsonBySchemaTool local validation tool
     * @param router chat client router for LLM-assisted validation
     * @param objectMapper JSON parser for responses
     * @throws NullPointerException if any argument is null
     */
    public VerifyJsonByJsonSchemaNode(
            final ValidateJsonBySchemaTool validateJsonBySchemaTool,
            final ChatClientRouter router,
            final ObjectMapper objectMapper) {
        this.validateJsonBySchemaTool = Objects.requireNonNull(validateJsonBySchemaTool, "validateJsonBySchemaTool");
        this.router = Objects.requireNonNull(router, "router");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Marker used when validation passes without errors.
     */
    /** Display marker for successful validation. */
    private static final String OK = "OK";

    /**
     * Marker used when the validator response cannot be interpreted.
     */
    /** Display marker when validation cannot be interpreted. */
    private static final String UNKNOWN = "UNKNOWN";

    /** Prompt template used to instruct the model to call the validation tool. */
    private static final String PROMPT_TEMPLATE =
            """
            Validate the JSON against the schema by calling the tool `validateJsonAgainstJsonSchema`.
            Respond ONLY with the tool JSON: {\"ok\":true} or {\"ok\":false,\"errors\":[\"...\"]}.

            JSON:
            %s

            SCHEMA:
            %s
            """;
    /** System instruction reinforcing the tool-call-only requirement. */
    private static final String SYSTEM_INSTRUCTION =
            """
            You MUST call the tool to validate. Return only the tool's JSON output.
            No explanations, no markdown.
            """;
    /** Display text for unknown validator result. */
    private static final String UNKNOWN_RESULT = "Unknown validation error";

    /**
     * Validates state[GENERATED_JSON] against state[JSON_SCHEMA] using the model-assisted tool call
     * with a deterministic local-tool fallback. Returns a compact display string and a stable
     * signature suitable for routing and deduplication.
     *
     * @param state current state; must contain GENERATED_JSON and JSON_SCHEMA
     * @return immutable map with keys VALIDATION_RESULT and VALIDATION_SIGNATURE
     */
    @Override
    public Map<String, Object> apply(@NonNull final AgentState state) {
        log.info("▶️ Stage: VerifyJsonByJsonSchemaNode — starting");
        final String json = state.get(GENERATED_JSON);
        final String schema = state.get(JSON_SCHEMA);

        String content;
        try {
            content = router.forNode(getClass().getSimpleName())
                    .prompt(PROMPT_TEMPLATE.formatted(json, schema))
                    .system(SYSTEM_INSTRUCTION)
                    .tools(validateJsonBySchemaTool)
                    .call()
                    .content();
        } catch (RuntimeException e) {
            content = null;
        }

        if (content == null || content.isBlank()) {
            content = validateJsonBySchemaTool.validateJsonBySchema(json, schema);
        } else if (content.startsWith("```")) {
            content = stripFences(content);
        }

        final Optional<Map<String, Object>> parsedPrimary = parseValidation(content);
        if (parsedPrimary.isPresent()) return parsedPrimary.get();

        // Fallback: re-run tool locally and parse again; otherwise UNKNOWN
        final String fallback = validateJsonBySchemaTool.validateJsonBySchema(json, schema);
        final Optional<Map<String, Object>> parsedFallback = parseValidation(fallback);
        return parsedFallback.orElseGet(this::unknown);
    }

    /**
     * Removes Markdown code fences if present, otherwise returns the input unchanged.
     *
     * @param s text to normalize; not null
     * @return unfenced text
     */
    private String stripFences(@NonNull final String s) {
        if (!s.startsWith("```")) return s;
        return s.replaceAll("^```[a-zA-Z]*\\n|```$", "").trim();
    }

    /**
     * Parses the tool/LLM response. Accepts either {"ok":true} or {"ok":false,"errors":[...]}
     * and converts it into a compact display string and a stable signature.
     *
     * @param content raw JSON response; may be blank
     * @return Optional of result map when parsed, or empty when unparsable/invalid
     */
    private Optional<Map<String, Object>> parseValidation(final String content) {
        if (content == null || content.isBlank()) return Optional.empty();
        try {
            final JsonNode root = objectMapper.readTree(content);
            if (root.path("ok").asBoolean(false)) {
                return Optional.of(okResult());
            }
            final JsonNode errors = root.path("errors");
            if (!errors.isArray()) return Optional.empty();

            final Iterator<JsonNode> it = errors.elements();
            final List<String> parts = new ArrayList<>();
            while (it.hasNext()) {
                final String msg = it.next().asText();
                final String trimmed = msg == null ? "" : msg.trim();
                if (!trimmed.isEmpty()) parts.add(trimmed);
            }
            if (parts.isEmpty()) return Optional.empty();

            final String validationResult = String.join(" \n", parts);
            final String signature = parts.stream().sorted().collect(Collectors.joining("|"));
            return Optional.of(
                    Map.of(VALIDATION_RESULT.name(), validationResult, VALIDATION_SIGNATURE.name(), signature));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Builds the success result.
     *
     * @return map containing OK display text and signature
     */
    private Map<String, Object> okResult() {
        return Map.of(VALIDATION_RESULT.name(), OK, VALIDATION_SIGNATURE.name(), OK);
    }

    /**
     * Builds the unknown result used when validator output is missing or not understood.
     *
     * @return map containing unknown display text and signature
     */
    private Map<String, Object> unknown() {
        return Map.of(VALIDATION_RESULT.name(), UNKNOWN_RESULT, VALIDATION_SIGNATURE.name(), UNKNOWN);
    }
}
