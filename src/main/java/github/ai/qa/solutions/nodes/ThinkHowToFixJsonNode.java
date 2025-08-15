package github.ai.qa.solutions.nodes;

import static github.ai.qa.solutions.state.AgentState.StateKey.PLAN_FIX;
import static github.ai.qa.solutions.state.AgentState.StateKey.USER_PROMPT;
import static github.ai.qa.solutions.state.AgentState.StateKey.VALIDATION_RESULT;

import github.ai.qa.solutions.state.AgentState;
import github.ai.qa.solutions.tools.PlanFixProvider;
import java.util.Map;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Plans how to fix JSON to satisfy the schema based on validation errors and user context.
 *
 * <p><b>Invariants:</b> does not mutate input state directly; emits only PLAN_FIX update.
 * <b>Thread-safety:</b> stateless; safe to reuse.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * Map<String, Object> out = node.apply(state);
 * String plan = (String) out.get(StateKey.PLAN_FIX.name());
 * }</pre>
 *
 * @see github.ai.qa.solutions.tools.ThinkHowToFixJsonTool
 */
@Service
public class ThinkHowToFixJsonNode implements NodeAction<AgentState> {
    /** Logs node lifecycle. */
    private static final Logger log = LoggerFactory.getLogger(ThinkHowToFixJsonNode.class);
    /** Tool-like provider that generates a minimal-change fix plan. */
    private final PlanFixProvider thinkHowToFixJsonTool;

    /**
     * Creates the node.
     *
     * @param thinkHowToFixJsonTool collaborator producing the fix plan; must not be null
     */
    public ThinkHowToFixJsonNode(final PlanFixProvider thinkHowToFixJsonTool) {
        this.thinkHowToFixJsonTool = thinkHowToFixJsonTool;
    }

    /**
     * Produces a minimal-change fix plan for invalid JSON.
     *
     * @param state graph state; must contain VALIDATION_RESULT and USER_PROMPT
     * @return map update with PLAN_FIX containing the plan text (may be empty but never null)
     */
    @Override
    public Map<String, Object> apply(final AgentState state) {
        log.info("▶️ Stage: ThinkHowToFixJsonNode — starting");
        final String thought =
                thinkHowToFixJsonTool.thinkHowToFixJson(state.get(VALIDATION_RESULT), state.get(USER_PROMPT));
        return Map.of(PLAN_FIX.name(), thought);
    }
}
