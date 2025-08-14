package github.ai.qa.solutions.nodes;

import static github.ai.qa.solutions.state.AgentState.StateKey.GENERATED_JSON;
import static github.ai.qa.solutions.state.AgentState.StateKey.JSON_SCHEMA;
import static github.ai.qa.solutions.state.AgentState.StateKey.PLAN_GENERATION;
import static github.ai.qa.solutions.state.AgentState.StateKey.USER_PROMPT;

import github.ai.qa.solutions.state.AgentState;
import github.ai.qa.solutions.tools.GenerateJsonBySchemaTool;
import java.util.Map;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GenerateJsonNode implements NodeAction<AgentState> {
    private static final Logger log = LoggerFactory.getLogger(GenerateJsonNode.class);
    private final GenerateJsonBySchemaTool generateJsonBySchemaTool;

    public GenerateJsonNode(final GenerateJsonBySchemaTool generateJsonBySchemaTool) {
        this.generateJsonBySchemaTool = generateJsonBySchemaTool;
    }

    @Override
    public Map<String, Object> apply(final AgentState state) {
        log.info("▶️ Stage: GenerateJsonNode — starting");
        final String generatedJson = generateJsonBySchemaTool.generateJsonBySchema(
                state.get(USER_PROMPT), state.get(JSON_SCHEMA), state.get(PLAN_GENERATION));
        return Map.of(GENERATED_JSON.name(), generatedJson);
    }
}
