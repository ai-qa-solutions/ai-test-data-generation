package github.ai.qa.solutions.nodes;

import github.ai.qa.solutions.state.AgentState;
import github.ai.qa.solutions.tools.GenerateJsonBySchemaTool;
import lombok.AllArgsConstructor;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Service;

import java.util.Map;

import static github.ai.qa.solutions.state.AgentState.StateKey.GENERATED_JSON;
import static github.ai.qa.solutions.state.AgentState.StateKey.PLAN_GENERATION;
import static github.ai.qa.solutions.state.AgentState.StateKey.JSON_SCHEMA;
import static github.ai.qa.solutions.state.AgentState.StateKey.USER_PROMPT;

@Service
@AllArgsConstructor
public class GenerateJsonNode implements NodeAction<AgentState> {
    private final GenerateJsonBySchemaTool generateJsonBySchemaTool;

    @Override
    public Map<String, Object> apply(final AgentState state) {
        final String generatedJson = generateJsonBySchemaTool.generateJsonBySchema(
                state.get(USER_PROMPT),
                state.get(JSON_SCHEMA),
                state.get(PLAN_GENERATION));
        return Map.of(GENERATED_JSON.name(), generatedJson);
    }
}