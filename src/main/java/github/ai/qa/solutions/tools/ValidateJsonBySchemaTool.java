package github.ai.qa.solutions.tools;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public record ValidateJsonBySchemaTool(SchemaVersionDetector versionDetector) {

    @Tool(
            name = "validateJsonAgainstJsonSchema",
            description = "Validates JSON data against provided JSON schema. "
                    + "Returns 'OK' for valid data or a newline-delimited list of validation errors.")
    public String validateJsonBySchema(
            @ToolParam(description = "JSON data to validate") String jsonTestData,
            @ToolParam(description = "JSON schema defining validation rules") String jsonSchema) {
        try {
            final JsonSchemaFactory factory = versionDetector.factoryWithFallback(jsonSchema);
            final Set<ValidationMessage> errors =
                    factory.getSchema(jsonSchema).validate(jsonTestData, InputFormat.JSON);
            if (errors.isEmpty()) {
                return "{\"ok\":true}";
            }
            final String joined = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .map(ValidateJsonBySchemaTool::quote)
                    .collect(Collectors.joining(","));
            return "{" + "\"ok\":false," + "\"errors\":[" + joined + "]" + "}";
        } catch (Exception e) {
            return "{" + "\"ok\":false," + "\"errors\":[" + quote(e.getMessage()) + "]" + "}";
        }
    }

    private static String quote(final String s) {
        return s == null ? "\"\"" : ("\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
    }
}
