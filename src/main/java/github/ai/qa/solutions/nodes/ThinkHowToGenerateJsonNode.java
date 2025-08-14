package github.ai.qa.solutions.nodes;

import static github.ai.qa.solutions.state.AgentState.StateKey.JSON_SCHEMA;
import static github.ai.qa.solutions.state.AgentState.StateKey.PLAN_GENERATION;
import static github.ai.qa.solutions.state.AgentState.StateKey.USER_PROMPT;

import github.ai.qa.solutions.state.AgentState;
import github.ai.qa.solutions.tools.ThinkHowToGenerateTool;
import java.util.Map;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ThinkHowToGenerateJsonNode implements NodeAction<AgentState> {
    private static final Logger log = LoggerFactory.getLogger(ThinkHowToGenerateJsonNode.class);
    private final ThinkHowToGenerateTool thinkHowToGenerateTool;

    public ThinkHowToGenerateJsonNode(final ThinkHowToGenerateTool thinkHowToGenerateTool) {
        this.thinkHowToGenerateTool = thinkHowToGenerateTool;
    }

    @Override
    public Map<String, Object> apply(final AgentState state) {
        log.info("▶️ Stage: ThinkHowToGenerateJsonNode — starting");
        final String schema = state.get(JSON_SCHEMA);
        final String userSpecificPromt = state.get(USER_PROMPT);
        final String thought = thinkHowToGenerateTool.thinkHowToGenerate(userSpecificPromt, schema);
        return Map.of(PLAN_GENERATION.name(), thought);
    }
}
