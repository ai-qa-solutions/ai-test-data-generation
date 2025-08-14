package github.ai.qa.solutions.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public record ValidateJsonSchemaTool(ObjectMapper objectMapper, SchemaVersionDetector versionDetector) {
    private static final Logger log = LoggerFactory.getLogger(ValidateJsonSchemaTool.class);

    @Tool(
            name = "validateJsonSchema",
            description = "Validates that input is a correct JSON Schema and returns a compacted JSON string")
    public String validateAndCompactSchema(
            @ToolParam(description = "JSON Schema to validate and compact") final String jsonSchema) {
        log.info("🛠️ coded as tool 💻: ValidateAndCompactSchema");
        try {
            // Auto-detect draft version and validate
            final JsonSchemaFactory factory = versionDetector.factoryWithFallback(jsonSchema);
            final String version = versionDetector.selectedVersion(jsonSchema).name();
            factory.getSchema(jsonSchema);
            // Compact representation
            final JsonNode jsonNode = objectMapper.readTree(jsonSchema);
            final String compact =
                    objectMapper.writeValueAsString(jsonNode).replace("\n", "").replace("\t", "");
            return "{" + "\"ok\":true,"
                    + "\"version\":"
                    + objectMapper.writeValueAsString(version) + "," + "\"compactSchema\":"
                    + objectMapper.writeValueAsString(compact) + "}";
        } catch (Exception e) {
            try {
                return "{\"ok\":false,\"error\":" + objectMapper.writeValueAsString(e.getMessage()) + "}";
            } catch (Exception ignored) {
                return "{\"ok\":false,\"error\":\"Unknown schema error\"}";
            }
        }
    }
}
