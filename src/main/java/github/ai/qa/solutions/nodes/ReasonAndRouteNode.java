package github.ai.qa.solutions.nodes;

import static github.ai.qa.solutions.state.AgentState.StateKey.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.ai.qa.solutions.services.ChatClientRouter;
import github.ai.qa.solutions.state.AgentState;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import org.bsc.langgraph4j.action.NodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReasonAndRouteNode implements NodeAction<AgentState> {
    private static final Logger log = LoggerFactory.getLogger(ReasonAndRouteNode.class);
    private final ChatClientRouter router;
    private final ObjectMapper objectMapper;

    public ReasonAndRouteNode(final ChatClientRouter router, final ObjectMapper objectMapper) {
        this.router = router;
        this.objectMapper = objectMapper;
    }

    private static final String DECISION_END = "END";
    private static final String DECISION_FIX = "FIX";
    private static final String DECISION_REGENERATE = "REGENERATE";

    @Override
    @SneakyThrows
    public Map<String, Object> apply(final AgentState state) {
        log.info("▶️ Stage: ReasonAndRouteNode — starting");
        final Map<String, Object> updates = new HashMap<>();

        final String validation = state.get(VALIDATION_RESULT);

        // Removed heuristic-driven FIX: if schema validation is OK, finish without extra realism passes

        // If already valid -> finish
        if ("OK".equalsIgnoreCase(validation)) {
            updates.put(DECISION.name(), DECISION_END);
            updates.put(REASONING.name(), "Validation passed. Finishing.");
            return updates;
        }

        // Track consecutive FIX attempts using ITERATION only for FIX steps
        int fixAttempts = 0;
        try {
            fixAttempts = Integer.parseInt(state.getOptional(ITERATION).orElse("0"));
        } catch (Exception ignored) {
        }
        final String prevDecision = state.getOptional(DECISION).orElse(null);
        final String prevValidation = state.getOptional(PREV_VALIDATION_RESULT).orElse(null);
        final String currSignature = state.getOptional(VALIDATION_SIGNATURE).orElse(null);
        final String prevSignature =
                state.getOptional(PREV_VALIDATION_SIGNATURE).orElse(null);
        if (!DECISION_FIX.equals(prevDecision)) {
            fixAttempts = 0; // reset counter when last decision wasn't FIX (e.g., REGENERATE or first run)
        }

        // If after a FIX the validation errors didn't change -> force regenerate
        if (DECISION_FIX.equals(prevDecision)
                && ((prevValidation != null && prevValidation.equals(validation))
                        || (prevSignature != null && currSignature != null && prevSignature.equals(currSignature)))) {
            updates.put(DECISION.name(), DECISION_REGENERATE);
            updates.put(REASONING.name(), "No progress after FIX; switching to REGENERATE.");
            updates.put(ITERATION.name(), "0");
            updates.put(PREV_VALIDATION_RESULT.name(), validation);
            if (currSignature != null) updates.put(PREV_VALIDATION_SIGNATURE.name(), currSignature);
            return updates;
        }

        // Heuristic: few errors -> try FIX first
        final int errorCount = countErrors(validation);
        if (errorCount <= 2) {
            updates.put(DECISION.name(), DECISION_FIX);
            updates.put(REASONING.name(), "Few errors (" + errorCount + ") → FIX.");
            updates.put(ITERATION.name(), String.valueOf(fixAttempts + 1));
            updates.put(PREV_VALIDATION_RESULT.name(), validation);
            if (currSignature != null) updates.put(PREV_VALIDATION_SIGNATURE.name(), currSignature);
            return updates;
        }

        final String userPrompt = state.get(USER_PROMPT);
        final String jsonSchema = state.get(JSON_SCHEMA);
        final String json = state.get(GENERATED_JSON);

        final String response = router.forNode("ReasonAndRouteNode")
                .prompt(
                        """
                        Decide next action to reach schema-valid JSON.
                        Choose only one: FIX (few local errors), REGENERATE (many/missing required/structure issues), END (already valid).

                        Return strictly this JSON: {"decision":"FIX|REGENERATE|END","reason":"<short>"}

                        Context:\n%s\n
                        Schema:\n%s\n
                        JSON:\n%s\n
                        Errors:\n%s\n
                        ConsecutiveFixAttempts: %d
                        """
                                .formatted(userPrompt, jsonSchema, json, validation, fixAttempts))
                .system(
                        """
                        Output only the compact JSON object with fields decision and reason.
                        - Prefer FIX for a small number of regex/format/enum issues.
                        - Prefer REGENERATE when many required fields are missing or object structure mismatches the schema.
                        - Never choose END unless errors are empty.
                        No markdown fences.
                        """)
                .call()
                .content();

        String decision;
        String reasoning;
        try {
            final JsonNode root = objectMapper.readTree(response);
            decision = root.path("decision").asText(DECISION_FIX).toUpperCase();
            reasoning = root.path("reason").asText("");
        } catch (Exception e) {
            // If parsing fails, default to FIX with captured response as reasoning
            decision = DECISION_FIX;
            reasoning = ("Routing JSON parse failed, default FIX. Raw: " + response);
        }

        if (!DECISION_FIX.equals(decision) && !DECISION_REGENERATE.equals(decision) && !DECISION_END.equals(decision)) {
            decision = DECISION_FIX;
        }

        updates.put(DECISION.name(), decision);
        updates.put(REASONING.name(), reasoning);
        if (DECISION_FIX.equals(decision)) {
            updates.put(ITERATION.name(), String.valueOf(fixAttempts + 1));
        } else if (DECISION_REGENERATE.equals(decision)) {
            updates.put(ITERATION.name(), "0");
        }
        updates.put(PREV_VALIDATION_RESULT.name(), validation);
        if (currSignature != null) updates.put(PREV_VALIDATION_SIGNATURE.name(), currSignature);
        return updates;
    }

    private int countErrors(final String validation) {
        if (validation == null || validation.isBlank() || "OK".equalsIgnoreCase(validation)) return 0;
        // errors are joined with " \n" in our validator
        final String[] parts = validation.split(" \\n");
        int c = 0;
        for (String p : parts) {
            if (!p.isBlank()) c++;
        }
        return c;
    }
}
