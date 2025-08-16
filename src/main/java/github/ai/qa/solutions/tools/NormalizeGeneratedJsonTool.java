package github.ai.qa.solutions.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.ai.qa.solutions.components.json.JsonNormalizer;
import github.ai.qa.solutions.components.json.JsonOutputSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Spring AI tool that normalizes JSON text and reports heuristics.
 */
@Component
public class NormalizeGeneratedJsonTool {
    /**
     * Logger for reporting tool execution.
     */
    private static final Logger log = LoggerFactory.getLogger(NormalizeGeneratedJsonTool.class);
    /**
     * Jackson mapper for parsing and serialization.
     */
    private final ObjectMapper objectMapper;
    /**
     * Sanitizer for raw model output.
     */
    private final JsonOutputSanitizer sanitizer;
    /**
     * Normalization service for JSON trees.
     */
    private final JsonNormalizer normalizer;

    /**
     * Full constructor for dependency injection.
     *
     * @param objectMapper     mapper instance
     * @param sanitizer        output sanitizer
     * @param normalizer       JSON normalizer
     */
    public NormalizeGeneratedJsonTool(
            final ObjectMapper objectMapper, final JsonOutputSanitizer sanitizer, final JsonNormalizer normalizer) {
        this.objectMapper = objectMapper;
        this.sanitizer = sanitizer;
        this.normalizer = normalizer;
    }

    /**
     * Tool entry point: normalizes input JSON text and returns a compact JSON response.
     *
     * @param inputJson raw JSON string possibly wrapped in code fences
     * @return JSON string: { ok, normalizedJson, warnings, signature } or { ok:false, error }
     */
    @Tool(name = "normalizeGeneratedJson", description = "Normalizes JSON text and reports heuristic warnings")
    public String normalize(
            @ToolParam(description = "Raw JSON text possibly wrapped in code fences") final String inputJson) {
        log.info("🛠️ coded as tool 💻: NormalizeGeneratedJson");
        try {
            final String stripped = sanitizer.stripFences(inputJson);
            final JsonNode root = objectMapper.readTree(stripped);
            final JsonNode normalized = normalizer.normalize(root);

            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            try {
                return "{\"ok\":false,\"error\":" + objectMapper.writeValueAsString(e.getMessage()) + "}";
            } catch (Exception ignored) {
                return "{\"ok\":false,\"error\":\"Unknown normalization error\"}";
            }
        }
    }
}
