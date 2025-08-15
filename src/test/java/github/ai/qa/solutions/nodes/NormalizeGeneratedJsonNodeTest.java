package github.ai.qa.solutions.nodes;

import static github.ai.qa.solutions.state.AgentState.StateKey.GENERATED_JSON;
import static github.ai.qa.solutions.state.AgentState.StateKey.HEURISTIC_SIGNATURE;
import static github.ai.qa.solutions.state.AgentState.StateKey.HEURISTIC_WARNINGS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.ai.qa.solutions.state.AgentState;
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
@Feature("Normalization")
@Owner("repo-maintainers")
@Tag("unit")
class NormalizeGeneratedJsonNodeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @Story("Strip markdown fences and NBSPs")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Normalizes fenced JSON and collapses NBSP to spaces")
    @Description("Removes fences and NBSPs; verifies heuristic outputs exist")
    void stripsMarkdownFencesAndNormalizesNbsp() throws Exception {
        String input = "```json\n{\n  \"name\": \"\u00A0John\u00A0Doe\u00A0\"\n}\n```";
        AgentState state = new AgentState(new HashMap<>(Map.of(GENERATED_JSON.name(), input)));

        NormalizeGeneratedJsonNode node = new NormalizeGeneratedJsonNode(mapper);
        Map<String, Object> out = node.apply(state);

        String json = (String) out.get(GENERATED_JSON.name());
        assertNotNull(json);

        // step: assert normalized output and heuristics present
        JsonNode root = mapper.readTree(json);
        assertEquals("John Doe", root.path("name").asText());
        assertNotNull(out.get(HEURISTIC_WARNINGS.name()));
        assertNotNull(out.get(HEURISTIC_SIGNATURE.name()));
    }

    @Test
    @Story("No warnings for normal values")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Produces empty heuristics when no placeholders found")
    @Description("Ensures normalized JSON has no warnings/signature for regular content")
    void producesEmptyHeuristicsWhenNoPlaceholders() throws Exception {
        String input = "{\n  \"name\": \" Alice \u00A0 Smith \"\n}";
        AgentState state = new AgentState(new HashMap<>(Map.of(GENERATED_JSON.name(), input)));

        NormalizeGeneratedJsonNode node = new NormalizeGeneratedJsonNode(mapper);
        Map<String, Object> out = node.apply(state);

        String json = (String) out.get(GENERATED_JSON.name());
        JsonNode root = mapper.readTree(json);
        // NBSP converted to space; internal sequence becomes three spaces
        assertEquals("Alice   Smith", root.path("name").asText());
        assertEquals("", out.get(HEURISTIC_WARNINGS.name()));
        assertEquals("", out.get(HEURISTIC_SIGNATURE.name()));
    }

    @Test
    @Story("Detect placeholder-like values")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Flags '123-456' as suspicious placeholder with signature")
    @Description("Heuristic should warn on placeholder literals and return non-empty signature")
    void flagsPlaceholderLiteralWithWarningAndSignature() throws Exception {
        String input = "{\n  \"phone\": \"123-456\"\n}";
        AgentState state = new AgentState(new HashMap<>(Map.of(GENERATED_JSON.name(), input)));

        NormalizeGeneratedJsonNode node = new NormalizeGeneratedJsonNode(mapper);
        Map<String, Object> out = node.apply(state);

        String warnings = (String) out.get(HEURISTIC_WARNINGS.name());
        String signature = (String) out.get(HEURISTIC_SIGNATURE.name());
        assertNotNull(warnings);
        assertNotNull(signature);
        // Path should include $/phone; both fields should be non-empty
        org.junit.jupiter.api.Assertions.assertTrue(warnings.contains("$/phone"));
        org.junit.jupiter.api.Assertions.assertFalse(warnings.isBlank());
        org.junit.jupiter.api.Assertions.assertFalse(signature.isBlank());
    }

    @Test
    @Story("Invalid input handling")
    @Severity(SeverityLevel.BLOCKER)
    @DisplayName("Throws when input is not valid JSON")
    @Description("Negative case: invalid JSON triggers IllegalStateException")
    void throwsWhenInputIsInvalidJson() {
        String input = "not a json document";
        AgentState state = new AgentState(new HashMap<>(Map.of(GENERATED_JSON.name(), input)));

        NormalizeGeneratedJsonNode node = new NormalizeGeneratedJsonNode(mapper);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> node.apply(state));
    }
}
