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
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.studio.springboot.AbstractLangGraphStudioConfig;
import org.bsc.langgraph4j.studio.springboot.LangGraphFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Central configuration for the agent flow without any intermediate topology model.
 *
 * <p>Highlights:</p>
 * <ul>
 *   <li>Type-safe node identifiers via {@link NodeId} and outcomes via {@link Decision}.</li>
 *   <li>Flow is built directly in {@link StateGraph} — no extra layers.</li>
 *   <li>Mermaid diagram is generated from the same constants (see {@link #agentFlowMermaid(StateGraph)}).</li>
 *   <li>Constructor injection and explicit bean contracts; no Lombok.</li>
 * </ul>
 *
 * <h2>Flow (Mermaid)</h2>
 *
 * <pre>{@code
 * graph TD
 *   START --> validate_schema
 *   validate_schema --> plan_generation
 *   plan_generation --> generate_initial_json
 *   generate_initial_json --> normalize_json
 *   normalize_json --> validate_json
 *   validate_json --> reason_and_route
 *   reason_and_route -- FIX --> plan_fix
 *   reason_and_route -- REGENERATE --> plan_generation
 *   reason_and_route -- END --> END
 *   plan_fix --> apply_fix
 *   apply_fix --> normalize_json
 * }</pre>
 */
@Configuration(proxyBeanMethods = false)
public class AgentApplicationConfiguration extends AbstractLangGraphStudioConfig {
    /** Framework logger for flow wiring diagnostics. */
    private static final Logger log = LoggerFactory.getLogger(AgentApplicationConfiguration.class);
    /** Application context used to resolve the built {@link LangGraphFlow}. */
    private final ApplicationContext context;

    public AgentApplicationConfiguration(final ApplicationContext context) {
        this.context = context;
    }

    /**
     * Exposes the {@link LangGraphFlow} to the studio infrastructure.
     *
     * @return the configured {@link LangGraphFlow}
     */
    @Override
    public LangGraphFlow getFlow() {
        return context.getBean(LangGraphFlow.class);
    }

    /**
     * Flow node identifiers. The {@link #id} values are stable and used in logs/metrics.
     */
    public enum NodeId {
        /**
         * Node: validate input JSON schema.
         */
        VALIDATE_SCHEMA("validate_schema"),
        /**
         * Node: think/plan how to generate JSON.
         */
        PLAN_GENERATION("plan_generation"),
        /**
         * Node: produce initial JSON.
         */
        GENERATE_INITIAL_JSON("generate_initial_json"),
        /**
         * Node: validate produced JSON against schema.
         */
        VALIDATE_JSON("validate_json"),
        /**
         * Node: reason on results and route next step.
         */
        REASON_AND_ROUTE("reason_and_route"),
        /**
         * Node: plan how to fix invalid JSON.
         */
        PLAN_FIX("plan_fix"),
        /**
         * Node: apply fixes to JSON.
         */
        APPLY_FIX("apply_fix"),
        /**
         * Node: normalize JSON (formatting/shape).
         */
        NORMALIZE_JSON("normalize_json");

        /**
         * Stable string identifier for a node.
         */
        public final String id;

        /**
         * Creates a node enum constant with the given string id.
         *
         * @param id stable identifier used inside the graph
         */
        NodeId(final String id) {
            this.id = id;
        }
    }

    /**
     * Decision produced by {@link NodeId#REASON_AND_ROUTE}.
     */
    public enum Decision {
        /**
         * End the flow.
         */
        END,
        /**
         * Regenerate JSON (go back to planning/generation).
         */
        REGENERATE,
        /**
         * Fix JSON (enter fix branch).
         */
        FIX
    }

    /**
     * Builds the {@link StateGraph} for {@link AgentState} using the provided node beans.
     *
     * @param validateJsonSchemaNode     node that validates the JSON schema
     * @param thinkHowToGenerateJsonNode node that plans JSON generation
     * @param generateJsonNode           node that generates initial JSON
     * @param verifyJsonByJsonSchemaNode node that validates JSON against schema
     * @param thinkHowToFixJsonNode      node that plans the fix for invalid JSON
     * @param fixErrorsInJsonNode        node that applies the fix to JSON
     * @param reasonAndRouteNode         node that decides the next step and emits {@link Decision}
     * @param normalizeGeneratedJsonNode node that normalizes produced JSON
     * @return the fully wired {@link StateGraph}
     * @throws org.bsc.langgraph4j.GraphStateException if the graph definition is inconsistent
     */
    @Bean
    public StateGraph<AgentState> stateGraph(
            final ValidateJsonSchemaNode validateJsonSchemaNode,
            final ThinkHowToGenerateJsonNode thinkHowToGenerateJsonNode,
            final GenerateJsonNode generateJsonNode,
            final VerifyJsonByJsonSchemaNode verifyJsonByJsonSchemaNode,
            final ThinkHowToFixJsonNode thinkHowToFixJsonNode,
            final FixErrorsInJsonNode fixErrorsInJsonNode,
            final ReasonAndRouteNode reasonAndRouteNode,
            final NormalizeGeneratedJsonNode normalizeGeneratedJsonNode)
            throws org.bsc.langgraph4j.GraphStateException {

        // Router that maps state[DECISION] to edge labels used in conditionalEdges
        final EdgeAction<AgentState> decisionRouter = state -> {
            final Object v = state.get(AgentState.StateKey.DECISION);
            final String s = String.valueOf(v);
            try {
                return switch (Decision.valueOf(s)) {
                    case END -> "end"; // will map to END target below
                    case REGENERATE -> "regenerate";
                    case FIX -> "fix";
                };
            } catch (IllegalArgumentException ex) {
                return "fix"; // safe default branch
            }
        };

        return new StateGraph<>(AgentState.SCHEMA, AgentState::new)
                .addNode(NodeId.VALIDATE_SCHEMA.id, node_async(validateJsonSchemaNode))
                .addNode(NodeId.PLAN_GENERATION.id, node_async(thinkHowToGenerateJsonNode))
                .addNode(NodeId.GENERATE_INITIAL_JSON.id, node_async(generateJsonNode))
                .addNode(NodeId.VALIDATE_JSON.id, node_async(verifyJsonByJsonSchemaNode))
                .addNode(NodeId.REASON_AND_ROUTE.id, node_async(reasonAndRouteNode))
                .addNode(NodeId.NORMALIZE_JSON.id, node_async(normalizeGeneratedJsonNode))
                .addNode(NodeId.APPLY_FIX.id, node_async(fixErrorsInJsonNode))
                .addNode(NodeId.PLAN_FIX.id, node_async(thinkHowToFixJsonNode))

                // Linear edges
                .addEdge(START, NodeId.VALIDATE_SCHEMA.id)
                .addEdge(NodeId.VALIDATE_SCHEMA.id, NodeId.PLAN_GENERATION.id)
                .addEdge(NodeId.PLAN_GENERATION.id, NodeId.GENERATE_INITIAL_JSON.id)
                .addEdge(NodeId.GENERATE_INITIAL_JSON.id, NodeId.NORMALIZE_JSON.id)
                .addEdge(NodeId.NORMALIZE_JSON.id, NodeId.VALIDATE_JSON.id)
                .addEdge(NodeId.VALIDATE_JSON.id, NodeId.REASON_AND_ROUTE.id)

                // Conditional edges from REASON_AND_ROUTE
                .addConditionalEdges(
                        NodeId.REASON_AND_ROUTE.id,
                        AsyncEdgeAction.edge_async(decisionRouter),
                        Map.of(
                                "end", END,
                                "fix", NodeId.PLAN_FIX.id,
                                "regenerate", NodeId.PLAN_GENERATION.id))

                // Fix branch
                .addEdge(NodeId.PLAN_FIX.id, NodeId.APPLY_FIX.id)
                .addEdge(NodeId.APPLY_FIX.id, NodeId.NORMALIZE_JSON.id);
    }

    /**
     * Creates a {@link LangGraphFlow} wrapper for studio integration and external triggering.
     *
     * @param stateGraph the wired state graph
     * @return a {@link LangGraphFlow} instance
     */
    @Bean
    public LangGraphFlow langGraphFlow(final StateGraph<AgentState> stateGraph) {
        return LangGraphFlow.builder()
                .title("(AITDG) AI Test Data Generation 🛟")
                .stateGraph(stateGraph)
                .addInputStringArg(USER_PROMPT.name())
                .addInputStringArg(JSON_SCHEMA.name())
                .build();
    }

    /**
     * Returns a Mermaid diagram of the flow computed from the same constants and flags.
     * Useful for logs, Actuator endpoints, tests, and documentation.
     *
     * @param stateGraph the wired state graph
     * @return Mermaid graph in "graph TD" syntax
     */
    @Bean(name = "agentFlowMermaid")
    public String agentFlowMermaid(final StateGraph<AgentState> stateGraph) {
        final String render = stateGraph
                .getGraph(GraphRepresentation.Type.MERMAID, "(AITDG) AI Test Data Generation", true)
                .content();
        log.info("Mermaid flow rendered:\n{}", render);
        return render;
    }
}
