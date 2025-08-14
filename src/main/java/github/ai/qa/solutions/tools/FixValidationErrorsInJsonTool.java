package github.ai.qa.solutions.tools;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public record FixValidationErrorsInJsonTool(ChatClient chatClient) {

    @Tool(
            name = "fixJsonValidationErrors",
            description = "Corrects JSON validation errors by applying structured recommendations while strictly "
                    + "adhering to JSON schema constraints. Outputs RFC8259-compliant JSON.")
    public String fixJsonByErrorsAndSchema(
            @ToolParam(description = "Raw validation error messages from schema validation") String validationErrors,
            @ToolParam(description = "Original JSON data requiring validation fixes") String jsonTestData,
            @ToolParam(description = "JSON schema defining data structure and validation rules") String jsonSchema,
            @ToolParam(description = "Structured error correction recommendations") String recommendation) {
        final String promt =
                """
                    Apply the corrections to the JSON so it validates against the schema.

                    Recommendations:
                    %s

                    Errors:
                    %s

                    Current JSON:
                    %s

                    JSON Schema:
                    %s

                    Output Rules:
                    - Return ONLY the corrected JSON object (RFC8259). No markdown, no comments, no extra text.
                    - Modify only fields implicated by the errors or directly required to satisfy constraints.
                    - Keep other valid fields unchanged.
                    - Respect enum/const, patterns, formats, ranges, required, and additionalProperties.
                    - Normalize common pitfalls where applicable: use '-' not unicode dashes; strip a leading '+' when a pattern requires ddd-ddd; ensure digits are ASCII.
                    - Prefer realistic, lifelike values; avoid placeholder values such as "Иванов Иван Иванович" or "123456789" where relevant.
                    - Anti-Placeholder Policy: do not produce monotonic sequences (123…, 321…), all-equal digits (000…, 111…), trivial grouped numbers (123-456), or dummy words (test, example). Where possible, use values consistent with locale (Санкт‑Петербург phones like +7 921/931/***, unit_code 780-***), and prefer values that satisfy known checksums (INN/OGRN/SNILS) or at least look non-trivial.
                    - Prefer simplest valid values for determinism.
                    """;
        return chatClient
                .prompt(promt.formatted(recommendation, validationErrors, jsonTestData, jsonSchema))
                .system(
                        """
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
