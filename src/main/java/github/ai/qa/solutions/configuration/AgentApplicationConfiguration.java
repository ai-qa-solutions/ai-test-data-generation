package github.ai.qa.solutions.configuration;

import static github.ai.qa.solutions.state.AgentState.StateKey.JSON_SCHEMA;
import static github.ai.qa.solutions.state.AgentState.StateKey.USER_PROMPT;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

import github.ai.qa.solutions.nodes.FixErrorsInJsonNode;
import github.ai.qa.solutions.nodes.GenerateJsonNode;
import github.ai.qa.solutions.nodes.NormalizeGeneratedJsonNode;
import github.ai.qa.solutions.nodes.ReasonAndRouteNode;
import github.ai.qa.solutions.nodes.ThinkHowToFixJsonNode;
import github.ai.qa.solutions.nodes.ThinkHowToGenerateJsonNode;
import github.ai.qa.solutions.nodes.ValidateJsonSchemaNode;
import github.ai.qa.solutions.nodes.VerifyJsonByJsonSchemaNode;
import github.ai.qa.solutions.state.AgentState;
import java.util.Map;
import lombok.SneakyThrows;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.studio.springboot.LangGraphFlow;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
            final FixErrorsInJsonNode fixErrorsInJsonNode,
            final ReasonAndRouteNode reasonAndRouteNode,
            final NormalizeGeneratedJsonNode normalizeGeneratedJsonNode) {
        final String END_FLOW = "end";
        final String DECIDE_FIX = "fix";
        final String DECIDE_REGENERATE = "regenerate";

        final String VALIDATE_SCHEMA = "validate_schema";
        final String PLAN_GENERATION = "plan_generation";
        final String GENERATE_INITIAL_JSON = "generate_initial_json";
        final String VALIDATE_JSON = "validate_json";
        final String REASON_AND_ROUTE = "reason_and_route";
        final String PLAN_FIX = "plan_fix";
        final String APPLY_FIX = "apply_fix";
        final String NORMALIZE_JSON = "normalize_json";

        final EdgeAction<AgentState> decisionRouter = state -> switch (state.get(AgentState.StateKey.DECISION)) {
            case "END" -> END_FLOW;
            case "REGENERATE" -> DECIDE_REGENERATE;
            case "FIX" -> DECIDE_FIX;
            default -> DECIDE_FIX;
        };

        return new StateGraph<>(AgentState.SCHEMA, AgentState::new)
                .addNode(VALIDATE_SCHEMA, node_async(validateJsonSchemaNode))
                .addNode(PLAN_GENERATION, node_async(thinkHowToGenerateJsonNode))
                .addNode(GENERATE_INITIAL_JSON, node_async(generateJsonNode))
                .addNode(VALIDATE_JSON, node_async(verifyJsonByJsonSchemaNode))
                .addNode(REASON_AND_ROUTE, node_async(reasonAndRouteNode))
                .addNode(NORMALIZE_JSON, node_async(normalizeGeneratedJsonNode))
                .addNode(APPLY_FIX, node_async(fixErrorsInJsonNode))
                .addNode(PLAN_FIX, node_async(thinkHowToFixJsonNode))
                .addEdge(START, VALIDATE_SCHEMA)
                .addEdge(VALIDATE_SCHEMA, PLAN_GENERATION)
                .addEdge(PLAN_GENERATION, GENERATE_INITIAL_JSON)
                .addEdge(GENERATE_INITIAL_JSON, NORMALIZE_JSON)
                .addEdge(NORMALIZE_JSON, VALIDATE_JSON)
                .addEdge(VALIDATE_JSON, REASON_AND_ROUTE)
                .addConditionalEdges(
                        REASON_AND_ROUTE,
                        AsyncEdgeAction.edge_async(decisionRouter),
                        Map.of(
                                END_FLOW, END,
                                DECIDE_FIX, PLAN_FIX,
                                DECIDE_REGENERATE, PLAN_GENERATION))
                .addEdge(PLAN_FIX, APPLY_FIX)
                .addEdge(APPLY_FIX, NORMALIZE_JSON);
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
