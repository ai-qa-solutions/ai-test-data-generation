package github.ai.qa.solutions.nodes;

import static github.ai.qa.solutions.state.AgentState.StateKey.PLAN_FIX;
import static github.ai.qa.solutions.state.AgentState.StateKey.USER_PROMPT;
import static github.ai.qa.solutions.state.AgentState.StateKey.VALIDATION_RESULT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import github.ai.qa.solutions.state.AgentState;
import github.ai.qa.solutions.tools.PlanFixProvider;
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
@Feature("Planning Fix")
@Owner("repo-maintainers")
@Tag("unit")
class ThinkHowToFixJsonNodeTest {

    @Test
    @Story("Plan fix from errors and prompt")
    @Severity(SeverityLevel.CRITICAL)
    @DisplayName("Returns PLAN_FIX from tool output")
    @Description("Positive: tool returns non-empty plan which is propagated to state updates")
    void returnsPlanFixFromTool() {
        // Arrange
        PlanFixProvider tool = (errors, prompt) -> "do X then Y";
        ThinkHowToFixJsonNode node = new ThinkHowToFixJsonNode(tool);

        AgentState state = new AgentState(new HashMap<>(Map.of(
                VALIDATION_RESULT.name(), "ERR",
                USER_PROMPT.name(), "PROMPT")));

        // Act
        Map<String, Object> out = node.apply(state);

        // Assert
        assertNotNull(out.get(PLAN_FIX.name()));
        assertEquals("do X then Y", out.get(PLAN_FIX.name()));
    }

    @Test
    @Story("Handles blank tool output")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Propagates blank string without errors")
    @Description("Negative: tool returns blank plan; node still emits PLAN_FIX key with blank value")
    void handlesBlankPlan() {
        // Arrange
        PlanFixProvider tool = (errors, prompt) -> "";
        ThinkHowToFixJsonNode node = new ThinkHowToFixJsonNode(tool);

        AgentState state = new AgentState(new HashMap<>(Map.of(
                VALIDATION_RESULT.name(), "some error",
                USER_PROMPT.name(), "context")));

        // Act
        Map<String, Object> out = node.apply(state);

        // Assert
        assertNotNull(out.get(PLAN_FIX.name()));
        assertEquals("", out.get(PLAN_FIX.name()));
    }
}
