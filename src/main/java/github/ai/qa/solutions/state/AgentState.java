package github.ai.qa.solutions.state;

import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.HashMap;
import java.util.Map;

public class AgentState extends org.bsc.langgraph4j.state.AgentState {

    public enum StateKey {
        USER_PROMPT,
        GENERATED_JSON,
        VALIDATION_RESULT,
        JSON_SCHEMA,
        PLAN_GENERATION,
        PLAN_FIX
    }

    public static final Map<String, Channel<?>> SCHEMA = initSchema();

    private static Map<String, Channel<?>> initSchema() {
        final Map<String, Channel<?>> schemaMap = new HashMap<>();
        final Channel<?> defaultChannel = Channels.base((current, update) -> update);
        for (StateKey key : StateKey.values()) {
            schemaMap.put(key.name(), defaultChannel);
        }
        return schemaMap;
    }

    public AgentState(Map<String, Object> initData) {
        super(initData);
    }

    public String get(final StateKey key) {
        return this.<String>value(key.name())
                .orElseThrow(() -> new IllegalStateException("Key did not found into AgentState: " + key));
    }
}