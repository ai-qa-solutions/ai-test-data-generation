package github.ai.qa.solutions.nodes;

import static github.ai.qa.solutions.state.AgentState.StateKey.PLAN_FIX;
import static github.ai.qa.solutions.state.AgentState.StateKey.USER_PROMPT;
import static github.ai.qa.solutions.state.AgentState.StateKey.VALIDATION_RESULT;

import github.ai.qa.solutions.state.AgentState;
import github.ai.qa.solutions.tools.ThinkHowToFixJsonTool;
import java.util.Map;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ThinkHowToFixJsonNode implements NodeAction<AgentState> {
    private static final Logger log = LoggerFactory.getLogger(ThinkHowToFixJsonNode.class);
    private final ThinkHowToFixJsonTool thinkHowToFixJsonTool;

    public ThinkHowToFixJsonNode(final ThinkHowToFixJsonTool thinkHowToFixJsonTool) {
        this.thinkHowToFixJsonTool = thinkHowToFixJsonTool;
    }

    @Override
    public Map<String, Object> apply(final AgentState state) {
        log.info("▶️ Stage: ThinkHowToFixJsonNode — starting");
        final String thought =
                thinkHowToFixJsonTool.thinkHowToFixJson(state.get(VALIDATION_RESULT), state.get(USER_PROMPT));
        return Map.of(PLAN_FIX.name(), thought);
    }
}
