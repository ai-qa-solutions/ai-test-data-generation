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
}
