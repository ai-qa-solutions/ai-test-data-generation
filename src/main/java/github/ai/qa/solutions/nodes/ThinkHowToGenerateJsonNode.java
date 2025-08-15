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
    /** Logs node lifecycle. */
    private static final Logger log = LoggerFactory.getLogger(ThinkHowToGenerateJsonNode.class);
    /** Tool that creates a structured generation plan. */
    private final ThinkHowToGenerateTool thinkHowToGenerateTool;

    public ThinkHowToGenerateJsonNode(final ThinkHowToGenerateTool thinkHowToGenerateTool) {
        this.thinkHowToGenerateTool = thinkHowToGenerateTool;
    }

    /**
     * Thinks through how to generate data given the schema and user prompt.
     *
     * @param state current flow state
     * @return state delta with PLAN_GENERATION thought text
     */
    @Override
    public Map<String, Object> apply(final AgentState state) {
        log.info("▶️ Stage: ThinkHowToGenerateJsonNode — starting");
        final String schema = state.get(JSON_SCHEMA);
        final String userSpecificPromt = state.get(USER_PROMPT);
        final String thought = thinkHowToGenerateTool.thinkHowToGenerate(userSpecificPromt, schema);
        return Map.of(PLAN_GENERATION.name(), thought);
    }
}
