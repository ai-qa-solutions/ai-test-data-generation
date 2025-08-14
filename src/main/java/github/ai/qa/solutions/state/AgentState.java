package github.ai.qa.solutions.state;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

public class AgentState extends org.bsc.langgraph4j.state.AgentState {

    public enum StateKey {
        USER_PROMPT,
        GENERATED_JSON,
        VALIDATION_RESULT,
        VALIDATION_SIGNATURE,
        JSON_SCHEMA,
        SCHEMA_VERSION,
        HEURISTIC_WARNINGS,
        HEURISTIC_SIGNATURE,
        PLAN_GENERATION,
        PLAN_FIX,
        DECISION,
        REASONING,
        ITERATION,
        PREV_VALIDATION_RESULT,
        PREV_VALIDATION_SIGNATURE
    }

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
