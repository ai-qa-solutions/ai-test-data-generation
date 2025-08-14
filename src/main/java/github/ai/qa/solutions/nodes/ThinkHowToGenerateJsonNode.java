package github.ai.qa.solutions.nodes;

import static github.ai.qa.solutions.state.AgentState.StateKey.JSON_SCHEMA;
import static github.ai.qa.solutions.state.AgentState.StateKey.PLAN_GENERATION;
import static github.ai.qa.solutions.state.AgentState.StateKey.USER_PROMPT;

import github.ai.qa.solutions.state.AgentState;
import github.ai.qa.solutions.tools.ThinkHowToGenerateTool;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class ThinkHowToGenerateJsonNode implements NodeAction<AgentState> {
    private final ThinkHowToGenerateTool thinkHowToGenerateTool;

    @Override
    public Map<String, Object> apply(final AgentState state) {
        log.info("▶️ Stage: ThinkHowToGenerateJsonNode — starting");
        final String schema = state.get(JSON_SCHEMA);
        final String userSpecificPromt = state.get(USER_PROMPT);
        final String thought = thinkHowToGenerateTool.thinkHowToGenerate(userSpecificPromt, schema);
        return Map.of(PLAN_GENERATION.name(), thought);
    }
}
