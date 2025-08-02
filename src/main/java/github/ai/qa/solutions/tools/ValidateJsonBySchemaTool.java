package github.ai.qa.solutions.tools;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public record ValidateJsonBySchemaTool() {

    @Tool(
            name = "validateJsonAgainstJsonSchema",
            description = "Validates JSON data against provided JSON schema. " +
                    "Returns 'OK' for valid data or a newline-delimited list of validation errors."
    )
    public String validateJsonBySchema(
            @ToolParam(description = "JSON data to validate") String jsonTestData,
            @ToolParam(description = "JSON schema defining validation rules") String jsonSchema
    ) {
        try {
            final Set<ValidationMessage> errors = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4)
                    .getSchema(jsonSchema)
                    .validate(jsonTestData, InputFormat.JSON);
            if (errors.isEmpty()) {
                return "OK";
            }
            return errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.joining(" \n"));
        } catch (Exception e) {
            return e.getMessage();
        }
    }
}
