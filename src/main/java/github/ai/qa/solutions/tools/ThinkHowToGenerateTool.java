package github.ai.qa.solutions.tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public record ThinkHowToGenerateTool(ChatClient chatClient) {

    @Tool(
            name = "thinkHowToGenerate",
            description = "Creates structured test data generation plan based on JSON schema constraints and test-specific requirements. Outputs step-by-step instructions."
    )
    public String thinkHowToGenerate(
            @ToolParam(description = "Test-specific constraints for data generation " +
                    "(language/region/format requirements)") final String userPromt,
            @ToolParam(description = "JSON schema defining data structure, " +
                    "validation rules, and field constraints") final String jsonSchema
    ) {
        return chatClient.prompt("""
                            Analyze the test context and JSON schema to provide structured recommendations
                            for filling test data.
                            
                            ### Test Context:
                            %s
                            
                            ### JSON Schema:
                            %s
                            
                            Response Structure:
                            1. Required Fields: List all `required` fields and their purpose.
                            2. Regex Constraints: For each field with a `pattern`:
                               - Explain the regex (e.g., `^[A-Z]{2}\\d{6}$` → 2 uppercase letters + 6 digits).
                               - Provide valid examples (e.g., `AB123456`).
                            3. Other critical Constraints:
                               - Format rules (`date`, `email`, `phone`).
                               - Length/numeric ranges (`minLength`, `maxLength`, `minimum`, `maximum`).
                            4. Data Recommendations:
                               - Realistic values based on inferred language (e.g., English names vs. Russian ФИО).
                               - How to avoid validation errors (e.g., avoid `_` in integers).
                            
                            Rules:
                            - Do NOT provide full JSON examples.
                            - Base recommendations strictly on the schema and test context.
                            - Use Russian-specific data (e.g., ФИО, СНИЛС passport numbers) ONLY IF the schema or context
                            implies Russian usage (e.g., `ФИО` in examples).
                            """.formatted(userPromt, jsonSchema)
                )
                .system("""
                            You are a test data analyst specializing in JSON schema compliance.
                            
                            Follow these rules:
                            1. Schema Analysis:
                               - Identify required fields, regex patterns, data types, and format constraints.
                               - Prioritize constraints like `minLength`, `maxLength`, `minimum`, and `maximum`.
                            2. Language Handling:
                               - Infer the target language from schema examples
                                (e.g., `ФИО` → Russian) or test context.
                               - Default to English unless Russian-specific terms
                               (e.g., `паспорт`, `ИНН`) are present.
                            3. Data Precision:
                               - Always decode regex patterns before suggesting examples
                               (e.g., `^\\+7\\d{10}$` → Russian phone number).
                               - Avoid invalid characters (e.g., `_` in integers).
                            4. Output Focus:
                               - Provide ONLY structured recommendations (no explanations, code, or JSON examples).
                               - Ensure recommendations align with schema validity and test context.
                            """
                )
                .call()
                .content();
    }
}
