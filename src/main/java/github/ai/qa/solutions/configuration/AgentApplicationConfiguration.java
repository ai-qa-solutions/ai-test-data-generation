package github.ai.qa.solutions.configuration;

import github.ai.qa.solutions.nodes.FixErrorsInJsonNode;
import github.ai.qa.solutions.nodes.GenerateJsonNode;
import github.ai.qa.solutions.nodes.ThinkHowToFixJsonNode;
import github.ai.qa.solutions.nodes.ThinkHowToGenerateJsonNode;
import github.ai.qa.solutions.nodes.ValidateJsonSchemaNode;
import github.ai.qa.solutions.nodes.VerifyJsonByJsonSchemaNode;
import github.ai.qa.solutions.state.AgentState;
import lombok.SneakyThrows;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.studio.springboot.LangGraphFlow;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.Map;

import static github.ai.qa.solutions.state.AgentState.StateKey.JSON_SCHEMA;
import static github.ai.qa.solutions.state.AgentState.StateKey.USER_PROMPT;
import static github.ai.qa.solutions.state.AgentState.StateKey.VALIDATION_RESULT;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Configuration
public class AgentApplicationConfiguration {

    @Bean
    @SneakyThrows
    public StateGraph<AgentState> stateGraph(
            final ValidateJsonSchemaNode validateJsonSchemaNode,
            final ThinkHowToGenerateJsonNode thinkHowToGenerateJsonNode,
            final GenerateJsonNode generateJsonNode,
            final VerifyJsonByJsonSchemaNode verifyJsonByJsonSchemaNode,
            final ThinkHowToFixJsonNode thinkHowToFixJsonNode,
            final FixErrorsInJsonNode fixErrorsInJsonNode
    ) {
        final String FIXED = "ok";
        final String VALIDATION_ERROR = "validation_error";

        final String VALIDATE_SCHEMA = "validate_schema";
        final String PLAN_GENERATION = "plan_generation";
        final String GENERATE_INITIAL_JSON = "generate_initial_json";
        final String VALIDATE_JSON = "validate_json";
        final String PLAN_FIX = "plan_fix";
        final String APPLY_FIX = "apply_fix";

        final EdgeAction<AgentState> isOk =
                state -> state.get(VALIDATION_RESULT).equals("OK") ? FIXED : VALIDATION_ERROR;

        return new StateGraph<>(AgentState.SCHEMA, AgentState::new)
                .addNode(VALIDATE_SCHEMA, node_async(validateJsonSchemaNode))
                .addNode(PLAN_GENERATION, node_async(thinkHowToGenerateJsonNode))
                .addNode(GENERATE_INITIAL_JSON, node_async(generateJsonNode))
                .addNode(VALIDATE_JSON, node_async(verifyJsonByJsonSchemaNode))
                .addNode(APPLY_FIX, node_async(fixErrorsInJsonNode))
                .addNode(PLAN_FIX, node_async(thinkHowToFixJsonNode))

                .addEdge(START, VALIDATE_SCHEMA)
                .addEdge(VALIDATE_SCHEMA, PLAN_GENERATION)
                .addEdge(PLAN_GENERATION, GENERATE_INITIAL_JSON)
                .addEdge(GENERATE_INITIAL_JSON, VALIDATE_JSON)
                .addConditionalEdges(
                        VALIDATE_JSON,
                        AsyncEdgeAction.edge_async(isOk),
                        Map.of(
                                FIXED, END,
                                VALIDATION_ERROR, PLAN_FIX)
                )
                .addEdge(PLAN_FIX, APPLY_FIX)
                .addEdge(APPLY_FIX, VALIDATE_JSON);
    }

    @Bean
    public LangGraphFlow langGraphFlow(final StateGraph<AgentState> stateGraph) {
        return LangGraphFlow.builder()
                .title("AI Test data generation")
                .stateGraph(stateGraph)
                .addInputStringArg(USER_PROMPT.name())
                .addInputStringArg(JSON_SCHEMA.name())
                .build();
    }
}
