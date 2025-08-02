package github.ai.qa.solutions.tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public record FixValidationErrorsInJsonTool(ChatClient chatClient) {

    @Tool(
            name = "fixJsonValidationErrors",
            description = "Corrects JSON validation errors by applying structured recommendations while strictly " +
                    "adhering to JSON schema constraints. Outputs RFC8259-compliant JSON."
    )
    public String fixJsonByErrorsAndSchema(
            @ToolParam(description = "Raw validation error messages from schema validation") String validationErrors,
            @ToolParam(description = "Original JSON data requiring validation fixes") String jsonTestData,
            @ToolParam(description = "JSON schema defining data structure and validation rules") String jsonSchema,
            @ToolParam(description = "Structured error correction recommendations") String recommendation
    ) {
        final String promt = """
                    Fix validation errors in the provided JSON data while strictly adhering to the JSON Schema.
                    
                    ### Recommendations for Corrections in json
                    %s
                    
                    ### Validation Errors to Fix:
                    %s
                    
                    ### Generated Test Data:
                    %s
                    
                    Your response should be in JSON format.
                    Do not include any explanations, only provide a RFC8259 compliant
                    JSON response following this format without deviation.
                    Do not include markdown code blocks in your response.
                    Remove the ```json markdown from the output.
                    
                    ### JSON Schema to Implement:
                    %s
                    """;
        return chatClient.prompt(promt.formatted(recommendation, validationErrors, jsonTestData, jsonSchema))
                .system("""
                            You are a precise JSON validator and fixer. Follow these rules:
                            1. Error Resolution:
                               - Analyze the validation errors and modify only the conflicting fields.
                               - Retain valid fields from the input JSON unless they violate schema constraints.
                            2. Language Handling:
                               - Infer the target language from schema examples (e.g., "ФИО" → Russian) or
                               the user’s original prompt or the generated test data.
                               - Default to English unless Russian-specific terms (e.g., "паспорт", "ИНН" and etc)
                               are present in the schema or errors.
                            3. Schema Compliance:
                               - Validate all fields against the schema’s format, regex, and type requirements.
                               - For integers: avoid underscores (`_`) and ensure numeric validity.
                               - For regex-defined fields: decode and match the pattern strictly
                               (e.g., `^\\+7\\d{10}$` → Russian phone number format).
                            4. Output Rules:
                               - Return **only** the corrected JSON object.
                               - Never include markdown, explanations, or metadata.
                               - Ensure deterministic output for identical inputs by strictly following the schema.
                            """)
                .call()
                .content();
    }
}
