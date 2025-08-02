package github.ai.qa.solutions.tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public record ThinkHowToFixJsonTool(ChatClient chatClient) {

    @Tool(
            name = "thinkHowToFixJson",
            description = "Analyzes JSON schema validation errors and provides structured recommendations to fix them while adhering to test-specific constraints."
    )
    public String thinkHowToFixJson(
            @ToolParam(description = "Raw validation errors output from schema validation process") final String errors,
            @ToolParam(description = "Test-specific scenario and data generation constraints") final String userPromt
    ) {
        return chatClient.prompt("""
                            Analyze validation errors and provide structured recommendations to fix them while
                            aligning with the test context.
                            
                            Validation Errors to Fix:
                            %s
                            
                            Test Context (Constraints):
                            %s
                            
                            Response Structure:
                            For EACH ERROR, provide:
                            1. Error Description: Summarize the validation issue
                            (e.g., regex mismatch, type error).
                            2. Root Cause: Explain why the error occurred
                            (e.g., "Value violates regex `^[A-Z]{2}\\d{6}$`").
                            3. Fix Recommendation:
                               - How to correct the value
                               (e.g., "Use 2 uppercase letters + 6 digits like `AB123456` for this field").
                               - Reference schema constraints (regex, data type, min/max length).
                            4. Context Alignment: Ensure fixes comply with test-specific requirements
                            (e.g., use Russian language if implied by Test Context or validation errors).
                            
                            Rules:
                            - Do NOT provide full JSON examples.
                            - Prioritize fixes that resolve errors without introducing new violations.
                            - For regex errors, decode the pattern first (e.g.,`^\\+7\\d{10}$` → Russian phone format).
                            - Avoid invalid characters (e.g., `_` in integers).
                            """.formatted(errors, userPromt)
                )
                .system("""
                            You are a test data analyst specializing in JSON schema validation fixes.
                            
                            Follow these rules:
                            1. Error Analysis:
                               - Break down each validation error into root causes
                               (regex, type, required fields, min/max constraints).
                               - Prioritize fixes that resolve the error without violating other schema rules.
                            2. Language Handling:
                               - Use the same language as the test context or schema examples (default to English).
                               - Apply Russian-specific data (e.g., ФИО, passport numbers, etc) ONLY IF the test
                               context or errors imply Russian usage (e.g., `ФИО` in examples).
                            3. Precision:
                               - Always decode regex patterns before suggesting fixes
                               (e.g., `^[A-Z]{2}\\d{6}$` → 2 uppercase letters + 6 digits).
                               - Avoid invalid characters (e.g., `_` in integers).
                            4. Output Focus:
                               - Provide only structured recommendations (no explanations, code, or JSON examples).
                               - Align fixes with the test context
                               (e.g., use realistic Russian phone numbers if required).
                            """
                )
                .call()
                .content();
    }
}
