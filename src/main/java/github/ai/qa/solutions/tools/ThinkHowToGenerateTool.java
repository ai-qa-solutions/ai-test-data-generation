package github.ai.qa.solutions.tools;

import github.ai.qa.solutions.services.ChatClientRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public record ThinkHowToGenerateTool(ChatClientRouter router) {
    /** Logs tool execution details. */
    private static final Logger log = LoggerFactory.getLogger(ThinkHowToGenerateTool.class);

    @Tool(
            name = "thinkHowToGenerate",
            description =
                    "Creates structured test data generation plan based on JSON schema constraints and test-specific requirements. Outputs step-by-step instructions.")
    public String thinkHowToGenerate(
            @ToolParam(
                            description = "Test-specific constraints for data generation "
                                    + "(language/region/format requirements)")
                    final String userPromt,
            @ToolParam(description = "JSON schema defining data structure, validation rules, and field constraints")
                    final String jsonSchema) {

        log.info("🛠️ Agent as tool 🤖: ThinkHowToGenerateTool");

        return router.forNode("ThinkHowToGenerateTool")
                .prompt(
                        """
                        Analyze the test context and the JSON Schema and produce a concise, actionable plan for generating valid test data.

                        Test Context:
                        %s

                        JSON Schema:
                        %s

                        Output strictly in the following sections (plain text, no JSON, no code):
                        1) Required Fields: bullet list name: purpose
                        2) Regex/Format Hints: for each field with pattern/format
                           - decode the constraint (e.g., ^\\+7\\d{10}$ → starts with +7 plus 10 digits)
                           - give 1–2 valid example values
                        3) Ranges/Lengths: summarize key min/max/minLength/maxLength/multipleOf
                        4) Data Strategy: realistic locale-aware suggestions (names, phones, addresses)
                        5) Pitfalls: common mistakes to avoid (e.g., unicode dashes vs '-', leading '+', wrong INN length)
                        6) Realism: prefer lifelike, non-placeholder values (avoid examples like "Иванов Иван Иванович" or "123456789"; choose plausible Russian names, addresses, organizations, and IDs)
                        7) Anti-Placeholder Checklist: avoid sequences like 123456 / 654321, repeated digits (000000, 111111), trivial groups (123-456), dummy words (test, example), and overused samples (+79211234567). Prefer varied digits and plausible distributions (e.g., Санкт‑Петербург → +7 921/931/999 ranges; unit_code like 780‑xxx).

                        Do not provide JSON examples. Keep it focused and directly tied to the schema constraints and the context.
                        """
                                .formatted(userPromt, jsonSchema))
                .system(
                        """
                        You are a precise test data analyst.
                        - Extract constraints (required, enum/const, pattern, format, ranges) and turn them into specific do/don't guidance.
                        - Decode regex before suggesting examples.
                        - Infer locale from context or examples; prefer Russian where clearly implied.
                        - Prefer realistic, lifelike suggestions; avoid placeholders (e.g., "Иванов Иван Иванович", "123456789").
                        - Apply anti-placeholder checklist: no monotonic sequences, no all-equal digits, no dummy words or trivial patterns.
                        - Be concise, structured, and avoid speculation beyond the schema.
                        - Output only the textual plan sections; no JSON, no markdown fences.
                        """)
                .call()
                .content();
    }
}
