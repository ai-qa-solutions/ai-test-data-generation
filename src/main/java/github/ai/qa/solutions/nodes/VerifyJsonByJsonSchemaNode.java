package github.ai.qa.solutions.nodes;

import github.ai.qa.solutions.state.AgentState;
import github.ai.qa.solutions.tools.ValidateJsonBySchemaTool;
import lombok.AllArgsConstructor;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Service;

import java.util.Map;

import static github.ai.qa.solutions.state.AgentState.StateKey.GENERATED_JSON;
import static github.ai.qa.solutions.state.AgentState.StateKey.JSON_SCHEMA;
import static github.ai.qa.solutions.state.AgentState.StateKey.VALIDATION_RESULT;

@Service
@AllArgsConstructor
public class VerifyJsonByJsonSchemaNode implements NodeAction<AgentState> {
    private final ValidateJsonBySchemaTool validateJsonBySchemaTool;

    @Override
    public Map<String, Object> apply(final AgentState state) {
        final String json = state.get(GENERATED_JSON);
        final String schema = state.get(JSON_SCHEMA);
        final String result = validateJsonBySchemaTool.validateJsonBySchema(json, schema);
        return Map.of(VALIDATION_RESULT.name(), result);
    }
}