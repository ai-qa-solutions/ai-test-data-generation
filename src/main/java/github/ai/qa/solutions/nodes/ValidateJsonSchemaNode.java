package github.ai.qa.solutions.nodes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.action.NodeAction;

import github.ai.qa.solutions.state.AgentState;
import org.springframework.stereotype.Service;

import java.util.Map;

import static github.ai.qa.solutions.state.AgentState.StateKey.JSON_SCHEMA;

@Service
@AllArgsConstructor
public class ValidateJsonSchemaNode implements NodeAction<AgentState> {
    private final ObjectMapper objectMapper;

    @Override
    @SneakyThrows
    public Map<String, Object> apply(final AgentState state) {
        try {
            final String jsonSchema = state.get(JSON_SCHEMA);
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4).getSchema(jsonSchema);
            final JsonNode jsonNode = objectMapper.readTree(jsonSchema);
            final String compactSchema = objectMapper.writeValueAsString(jsonNode)
                    .replace("\n", "").replace("\t", "");
            return Map.of(JSON_SCHEMA.name(), compactSchema);
        } catch (Exception e) {
            throw new GraphStateException("Incorrect jsonSchema:" + e.getMessage());
        }
    }
}