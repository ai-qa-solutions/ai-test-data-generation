package github.ai.qa.solutions.tools;

import github.ai.qa.solutions.services.ChatClientRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public record GenerateJsonBySchemaTool(ChatClientRouter router) {
    private static final Logger log = LoggerFactory.getLogger(GenerateJsonBySchemaTool.class);

    @Tool(
            name = "generateJsonFromSchema",
            description = "Generates RFC8259-compliant JSON test data that strictly adheres to JSON schema "
                    + "constraints while incorporating test-specific requirements and generation recommendations.")
    public String generateJsonBySchema(
            @ToolParam(description = "Test-specific scenario and constraints for data generation")
                    final String userSpecificPromt,
            @ToolParam(description = "JSON schema") final String jsonSchema,
            @ToolParam(description = "Structured generation recommendations based on the scenario")
                    final String recommendation) {

        log.info("🛠️ Agent as tool 🤖: GenerateJsonBySchemaTool");

        return router.forNode("GenerateJsonBySchemaTool")
                .prompt(
                        """
                                Produce ONLY a single RFC8259-compliant JSON object that strictly conforms to the JSON Schema.

                                Test Scenario:
                                %s

                                Generation Plan:
                                %s

                                JSON Schema:
                                %s

                                Rules:
                                - Output JSON only (no markdown, no comments, no explanations, no trailing text).
                                - Include all and only the properties allowed by the schema (respect additionalProperties).
                                - Populate every required field.
                                - Obey enum/const constraints exactly.
                                - For pattern/format fields, produce values that match the regex/format.
                                - For date ranges, ensure values lie within min/max (inclusive).
                                - For numbers, obey min/max and use integers when multipleOf=1.
                                - Prefer Russian locale data if implied by context/schema examples.
                                - Prefer realistic, lifelike values; avoid placeholders like "Иванов Иван Иванович" or "123456789".
                                - Anti-Placeholder Policy: forbid monotonic sequences (e.g., 123456, 654321), all-equal digits (000000, 111111), trivial grouped numbers (123-456), and dummy words (test, example). Use varied digits and plausible distributions for the locale (e.g., Санкт‑Петербург → +7 921/***, unit_code 780-***).
                                - Where applicable (e.g., INN/OGRN/SNILS), prefer values that satisfy known checksum rules; if not certain, still avoid trivial sequences and ensure non‑obvious combinations matching patterns.
                                - Do not invent unrelated fields; keep changes minimal and deterministic.
                                """
                                .formatted(userSpecificPromt, recommendation, jsonSchema))
                .system(
                        """
                        You are a deterministic JSON generator.
                        - Return exactly one JSON object, nothing else.
                        - Follow the schema and the plan precisely.
                        - Decode regex and produce matching values.
                        - Prefer realistic, lifelike values consistent with context; avoid placeholder/dummy patterns.
                        - Enforce the Anti-Placeholder Policy: reject sequences 123…/000…/111…/123-456 and similar trivialities; choose plausible, non-sequential digits.
                        - Keep values consistent with the city/region in the context (e.g., Санкт‑Петербург phone ranges, unit_code region prefix).
                        - Prefer the simplest valid values to maximize determinism.
                        - Never wrap output in markdown fences.
                        """)
                .call()
                .content();
    }
}
