package github.ai.qa.solutions.tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public record ThinkHowToFixJsonTool(ChatClient chatClient) {

    @Tool(
            name = "thinkHowToFixJson",
            description =
                    "Analyzes JSON schema validation errors and provides structured recommendations to fix them while adhering to test-specific constraints.")
    public String thinkHowToFixJson(
            @ToolParam(description = "Raw validation errors output from schema validation process") final String errors,
            @ToolParam(description = "Test-specific scenario and data generation constraints") final String userPromt) {
        return chatClient
                .prompt(
                        """
                            Analyze the validation errors and propose a minimal-change fix plan consistent with the test context.

                            Errors:
                            %s

                            Context:
                            %s

                            Output strictly as text (no JSON). For each error include:
                            - Path: (e.g., #/passport_rf/unit_code)
                            - Issue: concise description (regex mismatch, missing required, type, range)
                            - Constraint: show decoded regex/format or min/max (e.g., ^\\d{3}-\\d{3}$ → ddd-ddd)
                            - Fix: exact change to make (only this field), provide 1 valid example value (realistic and lifelike; avoid placeholders like "Иванов Иван Иванович" or "123456789"). Avoid monotonic sequences (123…, 321…), all-equal digits (000…, 111…), trivial groups (123-456), and dummy words (test, example).
                            - Notes: avoid changing unrelated fields; consider normalization (+7 phones, '-' vs unicode dashes, strip leading '+')
                """
                                .formatted(errors, userPromt))
                .system(
                        """
                            You are a precise fixer.
                            - Suggest the smallest edits to pass validation.
                            - Do not modify fields not implicated by the errors unless absolutely required.
                            - Decode regex and ensure examples truly match.
                            - Respect locale and realism; keep Russian data where implied. Prefer realistic, lifelike examples; avoid placeholders and trivial digit patterns per the Anti-Placeholder policy.
                            - Output only the plan text; no JSON, no markdown fences.
                            """)
                .call()
                .content();
    }
}
