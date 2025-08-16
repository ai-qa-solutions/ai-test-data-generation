package github.ai.qa.solutions.nodes;

import static github.ai.qa.solutions.state.AgentState.StateKey.GENERATED_JSON;
import static github.ai.qa.solutions.state.AgentState.StateKey.JSON_SCHEMA;
import static github.ai.qa.solutions.state.AgentState.StateKey.VALIDATION_RESULT;
import static github.ai.qa.solutions.state.AgentState.StateKey.VALIDATION_SIGNATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import github.ai.qa.solutions.services.ChatClientRouter;
import github.ai.qa.solutions.state.AgentState;
import github.ai.qa.solutions.tools.SchemaVersionDetector;
import github.ai.qa.solutions.tools.ValidateJsonBySchemaTool;
import io.qameta.allure.Description;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Epic("AI Test Data Generation")
@Feature("Validation")
@Owner("repo-maintainers")
@Tag("unit")
class VerifyJsonByJsonSchemaNodeTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ValidateJsonBySchemaTool tool = new ValidateJsonBySchemaTool(new SchemaVersionDetector(mapper));
    private final ChatClientRouter router = node -> {
        throw new RuntimeException("no LLM in unit tests");
    };

    @Test
    @Story("Return OK and signature for valid JSON")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Valid JSON yields OK/OK result")
    @Description("Valid sample validates to OK and produces OK signature")
    void validJsonYieldsOk() {
        String schema =
                "{\n  \"type\": \"object\",\n  \"properties\": { \"name\": { \"type\": \"string\" } },\n  \"required\": [\"name\"]\n}";
        String json = "{\n  \"name\": \"Alice\"\n}";

        AgentState state = new AgentState(new HashMap<>(Map.of(
                JSON_SCHEMA.name(), schema,
                GENERATED_JSON.name(), json)));

        VerifyJsonByJsonSchemaNode node = new VerifyJsonByJsonSchemaNode(tool, router, mapper);
        Map<String, Object> out = node.apply(state);

        assertEquals("OK", out.get(VALIDATION_RESULT.name()));
        assertEquals("OK", out.get(VALIDATION_SIGNATURE.name()));
    }

    @Test
    @Story("Return errors joined + stable signature for invalid JSON")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Invalid JSON yields errors and signature")
    @Description("Invalid sample produces newline-joined errors and sorted signature")
    void invalidJsonYieldsErrors() {
        String schema =
                "{\n  \"type\": \"object\",\n  \"properties\": { \"age\": { \"type\": \"integer\" } },\n  \"required\": [\"age\"]\n}";
        String json = "{\n  \"age\": \"not-an-int\"\n}";

        AgentState state = new AgentState(new HashMap<>(Map.of(
                JSON_SCHEMA.name(), schema,
                GENERATED_JSON.name(), json)));

        VerifyJsonByJsonSchemaNode node = new VerifyJsonByJsonSchemaNode(tool, router, mapper);
        Map<String, Object> out = node.apply(state);

        String result = (String) out.get(VALIDATION_RESULT.name());
        String signature = (String) out.get(VALIDATION_SIGNATURE.name());

        // Expect non-empty errors and non-empty signature
        assertFalse(result == null || result.isBlank());
        assertFalse(signature == null || signature.isBlank());
    }
}
