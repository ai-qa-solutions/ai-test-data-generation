package github.ai.qa.solutions.state;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

public class AgentState extends org.bsc.langgraph4j.state.AgentState {

    /**
     * State keys carried through the graph.
     */
    public enum StateKey {
        /** User prompt driving generation. */
        USER_PROMPT,
        /** Latest generated JSON. */
        GENERATED_JSON,
        /** Validator display text (OK or newline-joined errors). */
        VALIDATION_RESULT,
        /** Sorted signature of validation errors. */
        VALIDATION_SIGNATURE,
        /** JSON Schema provided by the user. */
        JSON_SCHEMA,
        /** Detected JSON Schema version label. */
        SCHEMA_VERSION,
        /** Heuristic warnings gathered during normalization. */
        HEURISTIC_WARNINGS,
        /** Sorted signature of heuristic warnings. */
        HEURISTIC_SIGNATURE,
        /** Plan for generation. */
        PLAN_GENERATION,
        /** Plan for fixing invalid JSON. */
        PLAN_FIX,
        /** Routing decision. */
        DECISION,
        /** Reasoning behind the decision. */
        REASONING,
        /** Iteration counter for repeated FIX attempts. */
        ITERATION,
        /** Previous validator display text. */
        PREV_VALIDATION_RESULT,
        /** Previous validation signature. */
        PREV_VALIDATION_SIGNATURE
    }

    /** Unmodifiable schema mapping state keys to channels. */
    public static final Map<String, Channel<?>> SCHEMA = initSchema();

    private static Map<String, Channel<?>> initSchema() {
        final Map<String, Channel<?>> schemaMap = new HashMap<>();
        final Channel<?> defaultChannel = Channels.base((current, update) -> update);
        for (StateKey key : StateKey.values()) {
            schemaMap.put(key.name(), defaultChannel);
        }
        return java.util.Collections.unmodifiableMap(schemaMap);
    }

    public AgentState(Map<String, Object> initData) {
        super(initData);
    }

    public String get(final StateKey key) {
        return this.<String>value(key.name())
                .orElseThrow(() -> new IllegalStateException("Key did not found into AgentState: " + key));
    }

    public Optional<String> getOptional(final StateKey key) {
        return this.value(key.name());
    }
}
