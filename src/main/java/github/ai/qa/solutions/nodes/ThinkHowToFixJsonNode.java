package github.ai.qa.solutions.nodes;

import github.ai.qa.solutions.state.AgentState;
import github.ai.qa.solutions.tools.ThinkHowToFixJsonTool;
import lombok.AllArgsConstructor;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Service;

import java.util.Map;

import static github.ai.qa.solutions.state.AgentState.StateKey.PLAN_FIX;
import static github.ai.qa.solutions.state.AgentState.StateKey.USER_PROMPT;
import static github.ai.qa.solutions.state.AgentState.StateKey.VALIDATION_RESULT;

@Service
@AllArgsConstructor
public class ThinkHowToFixJsonNode implements NodeAction<AgentState> {
    private final ThinkHowToFixJsonTool thinkHowToFixJsonTool;

    @Override
    public Map<String, Object> apply(final AgentState state) {
        final String thought = thinkHowToFixJsonTool.thinkHowToFixJson(
                state.get(VALIDATION_RESULT), state.get(USER_PROMPT));
        return Map.of(PLAN_FIX.name(), thought);
    }
}