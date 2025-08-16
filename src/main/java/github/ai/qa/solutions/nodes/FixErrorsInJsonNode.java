package github.ai.qa.solutions.nodes;

import static github.ai.qa.solutions.state.AgentState.StateKey.GENERATED_JSON;
import static github.ai.qa.solutions.state.AgentState.StateKey.JSON_SCHEMA;
import static github.ai.qa.solutions.state.AgentState.StateKey.PLAN_FIX;
import static github.ai.qa.solutions.state.AgentState.StateKey.VALIDATION_RESULT;

import github.ai.qa.solutions.state.AgentState;
import github.ai.qa.solutions.tools.FixValidationErrorsInJsonTool;
import java.util.Map;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FixErrorsInJsonNode implements NodeAction<AgentState> {
    /** Logs node lifecycle. */
    private static final Logger log = LoggerFactory.getLogger(FixErrorsInJsonNode.class);
    /** Tool that applies recommended fixes to invalid JSON. */
    private final FixValidationErrorsInJsonTool fixValidationErrorsInJsonTool;

    public FixErrorsInJsonNode(final FixValidationErrorsInJsonTool fixValidationErrorsInJsonTool) {
        this.fixValidationErrorsInJsonTool = fixValidationErrorsInJsonTool;
    }

    /**
     * Applies LLM-recommended fixes to the JSON failing validation.
     *
     * @param state current flow state
     * @return state delta containing the corrected JSON
     */
    @Override
    public Map<String, Object> apply(final AgentState state) {
        log.info("▶️ Stage: FixErrorsInJsonNode — starting");
        final String fixedJson = fixValidationErrorsInJsonTool.fixJsonByErrorsAndSchema(
                state.get(VALIDATION_RESULT), state.get(GENERATED_JSON), state.get(JSON_SCHEMA), state.get(PLAN_FIX));
        return Map.of(GENERATED_JSON.name(), fixedJson);
    }
}
