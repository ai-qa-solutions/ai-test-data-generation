package github.ai.qa.solutions.tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public record GenerateJsonBySchemaTool(ChatClient chatClient) {

    @Tool(
            name = "generateJsonFromSchema",
            description = "Generates RFC8259-compliant JSON test data that strictly adheres to JSON schema " +
                    "constraints while incorporating test-specific requirements and generation recommendations."
    )
    public String generateJsonBySchema(
            @ToolParam(description = "Test-specific scenario and constraints for data generation") final String userSpecificPromt,
            @ToolParam(description = "JSON schema") final String jsonSchema,
            @ToolParam(description = "Structured generation recommendations based on the scenario") final String recommendation
    ) {
        return chatClient.prompt(
                        """
                                Generate test data EXCLUSIVELY as JSON that strictly adheres to the JSON Schema.
                                
                                ### Test Scenario Specifications:
                                %s
                                
                                ### Data Generation Recommendations:
                                %s
                                
                                Your response should be in JSON format.
                                Do not include any explanations, only provide a RFC8259 compliant
                                JSON response following this format without deviation.
                                Do not include markdown code blocks in your response.
                                Remove the ```json markdown from the output.
                                
                                ### JSON Schema to Implement:
                                %s
                                """.formatted(userSpecificPromt, recommendation, jsonSchema)
                )
                .system("""
                        You are a precise JSON generator.
                        Follow these rules:
                        1. Always validate generated data against the provided JSON Schema.
                        2. Language Detection:
                           - Infer the target language from the schema’s example values
                           (if any) or the user’s prompt.
                           - Default to English unless Russian-specific terms
                           (e.g., "паспорт", "ФИО") or schema examples explicitly require Russian data.
                        3. For integers: avoid underscores (`_`) and ensure numeric validity.
                        4. For regex-defined fields: decode the regex first
                        (e.g., `^[A-Z]{2}\\d{6}$` → 2 uppercase letters + 6 digits)
                        5. Never include markdown, explanations, or metadata—return **only** the JSON object.
                        6. Ensure deterministic output for identical inputs by strictly following the schema.
                        """
                )
                .call()
                .content();
    }
}
