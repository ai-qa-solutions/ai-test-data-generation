package github.ai.qa.solutions.nodes;

import static github.ai.qa.solutions.state.AgentState.StateKey.GENERATED_JSON;
import static github.ai.qa.solutions.state.AgentState.StateKey.JSON_SCHEMA;
import static github.ai.qa.solutions.state.AgentState.StateKey.VALIDATION_RESULT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import github.ai.qa.solutions.state.AgentState;
import github.ai.qa.solutions.tools.ValidateJsonBySchemaTool;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class VerifyJsonByJsonSchemaNode implements NodeAction<AgentState> {
    private final ValidateJsonBySchemaTool validateJsonBySchemaTool;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Override
    @SneakyThrows
    public Map<String, Object> apply(final AgentState state) {
        final String json = state.get(GENERATED_JSON);
        final String schema = state.get(JSON_SCHEMA);

        String content;
        try {
            content = chatClient
                    .prompt(
                            """
                        Validate the JSON against the schema by calling the tool `validateJsonAgainstJsonSchema`.
                        Respond ONLY with the tool JSON: {"ok":true} or {"ok":false,"errors":["..."]}.

                        JSON:
                        %s

                        SCHEMA:
                        %s
                        """
                                    .formatted(json, schema))
                    .system(
                            """
                        You MUST call the tool to validate. Return only the tool's JSON output.
                        No explanations, no markdown.
                        """)
                    .tools(validateJsonBySchemaTool)
                    .call()
                    .content();
        } catch (Exception e) {
            content = null;
        }

        if (content == null || content.isBlank()) {
            content = validateJsonBySchemaTool.validateJsonBySchema(json, schema);
        } else if (content.startsWith("```")) {
            content = content.replaceAll("^```[a-zA-Z]*\\n|```$", "").trim();
        }

        String validationResult;
        final JsonNode root = objectMapper.readTree(content);
        final boolean ok = root.path("ok").asBoolean(false);
        String signature;
        if (ok) {
            validationResult = "OK";
            signature = "OK";
        } else {
            final JsonNode errors = root.path("errors");
            final StringBuilder sb = new StringBuilder();
            final StringBuilder sigBuilder = new StringBuilder();
            if (errors.isArray()) {
                final Iterator<JsonNode> it = errors.elements();
                java.util.List<String> parts = new java.util.ArrayList<>();
                while (it.hasNext()) {
                    final String msg = it.next().asText();
                    parts.add(msg.trim());
                }
                // Stable string for display
                for (int i = 0; i < parts.size(); i++) {
                    if (i > 0) sb.append(" \n");
                    sb.append(parts.get(i));
                }
                // Signature: sort and join
                signature = parts.stream().filter(s -> !s.isBlank()).sorted().collect(Collectors.joining("|"));
            } else {
                validationResult = "Unknown validation error";
                signature = "UNKNOWN";
                return Map.of(
                        VALIDATION_RESULT.name(),
                        validationResult,
                        github.ai.qa.solutions.state.AgentState.StateKey.VALIDATION_SIGNATURE.name(),
                        signature);
            }
            validationResult = sb.toString();
        }

        return Map.of(
                VALIDATION_RESULT.name(),
                validationResult,
                github.ai.qa.solutions.state.AgentState.StateKey.VALIDATION_SIGNATURE.name(),
                signature);
    }
}
