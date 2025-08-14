package github.ai.qa.solutions.nodes;

import static github.ai.qa.solutions.state.AgentState.StateKey.PLAN_FIX;
import static github.ai.qa.solutions.state.AgentState.StateKey.USER_PROMPT;
import static github.ai.qa.solutions.state.AgentState.StateKey.VALIDATION_RESULT;

import github.ai.qa.solutions.state.AgentState;
import github.ai.qa.solutions.tools.ThinkHowToFixJsonTool;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class ThinkHowToFixJsonNode implements NodeAction<AgentState> {
    private final ThinkHowToFixJsonTool thinkHowToFixJsonTool;

    @Override
    public Map<String, Object> apply(final AgentState state) {
        log.info("▶️ Stage: ThinkHowToFixJsonNode — starting");
        final String thought =
                thinkHowToFixJsonTool.thinkHowToFixJson(state.get(VALIDATION_RESULT), state.get(USER_PROMPT));
        return Map.of(PLAN_FIX.name(), thought);
    }
}
