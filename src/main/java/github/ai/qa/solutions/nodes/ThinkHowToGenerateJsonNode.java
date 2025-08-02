package github.ai.qa.solutions.nodes;

import github.ai.qa.solutions.state.AgentState;
import github.ai.qa.solutions.tools.ThinkHowToGenerateTool;
import lombok.AllArgsConstructor;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Service;

import java.util.Map;

import static github.ai.qa.solutions.state.AgentState.StateKey.PLAN_GENERATION;
import static github.ai.qa.solutions.state.AgentState.StateKey.JSON_SCHEMA;
import static github.ai.qa.solutions.state.AgentState.StateKey.USER_PROMPT;

@Service
@AllArgsConstructor
public class ThinkHowToGenerateJsonNode implements NodeAction<AgentState> {
    private final ThinkHowToGenerateTool thinkHowToGenerateTool;

    @Override
    public Map<String, Object> apply(final AgentState state) {
        final String schema = state.get(JSON_SCHEMA);
        final String userSpecificPromt = state.get(USER_PROMPT);
        final String thought = thinkHowToGenerateTool.thinkHowToGenerate(schema, userSpecificPromt);
        return Map.of(PLAN_GENERATION.name(), thought);
    }
}