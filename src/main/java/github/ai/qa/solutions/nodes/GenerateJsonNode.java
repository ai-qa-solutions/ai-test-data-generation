package github.ai.qa.solutions.nodes;

import static github.ai.qa.solutions.state.AgentState.StateKey.GENERATED_JSON;
import static github.ai.qa.solutions.state.AgentState.StateKey.JSON_SCHEMA;
import static github.ai.qa.solutions.state.AgentState.StateKey.PLAN_GENERATION;
import static github.ai.qa.solutions.state.AgentState.StateKey.USER_PROMPT;

import github.ai.qa.solutions.state.AgentState;
import github.ai.qa.solutions.tools.GenerateJsonBySchemaTool;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@AllArgsConstructor
public class GenerateJsonNode implements NodeAction<AgentState> {
    private final GenerateJsonBySchemaTool generateJsonBySchemaTool;

    @Override
    public Map<String, Object> apply(final AgentState state) {
        log.info("▶️ Stage: GenerateJsonNode — starting");
        final String generatedJson = generateJsonBySchemaTool.generateJsonBySchema(
                state.get(USER_PROMPT), state.get(JSON_SCHEMA), state.get(PLAN_GENERATION));
        return Map.of(GENERATED_JSON.name(), generatedJson);
    }
}
