package github.ai.qa.solutions.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public record SchemaVersionDetector(ObjectMapper objectMapper) {

    public SpecVersion.VersionFlag detectVersion(final String schemaText) {
        try {
            final JsonNode root = objectMapper.readTree(schemaText);
            final String schemaUrl = root.path("$schema").asText("");
            if (!schemaUrl.isEmpty()) {
                final String s = schemaUrl.toLowerCase();
                if (s.contains("2020-12")) {
                    return SpecVersion.VersionFlag.V202012;
                }
                if (s.contains("2019-09")) {
                    return SpecVersion.VersionFlag.V201909;
                }
                if (s.contains("draft-07") || s.contains("draft7")) {
                    return SpecVersion.VersionFlag.V7;
                }
                if (s.contains("draft-06") || s.contains("draft6")) {
                    return SpecVersion.VersionFlag.V6;
                }
                if (s.contains("draft-04") || s.contains("draft4")) {
                    return SpecVersion.VersionFlag.V4;
                }
            }
            // Heuristics when $schema is absent or unrecognized
            final String text = root.toString();
            if (text.contains("\"$defs\"")) {
                // $defs introduced in 2019-09
                return SpecVersion.VersionFlag.V201909;
            }
        } catch (Exception ignored) {
        }
        // conservative default
        return SpecVersion.VersionFlag.V4;
    }

    public List<SpecVersion.VersionFlag> detectCandidates(final String schemaText) {
        final SpecVersion.VersionFlag primary = detectVersion(schemaText);
        final List<SpecVersion.VersionFlag> order = new ArrayList<>();
        order.add(primary);
        // Add remaining versions by recency preference
        final EnumSet<SpecVersion.VersionFlag> all = EnumSet.of(
                SpecVersion.VersionFlag.V202012,
                SpecVersion.VersionFlag.V201909,
                SpecVersion.VersionFlag.V7,
                SpecVersion.VersionFlag.V6,
                SpecVersion.VersionFlag.V4);
        all.remove(primary);
        // Prefer newer first
        for (SpecVersion.VersionFlag v : List.of(
                SpecVersion.VersionFlag.V202012,
                SpecVersion.VersionFlag.V201909,
                SpecVersion.VersionFlag.V7,
                SpecVersion.VersionFlag.V6,
                SpecVersion.VersionFlag.V4)) {
            if (all.contains(v)) order.add(v);
        }
        return order;
    }

    public JsonSchemaFactory factoryWithFallback(final String schemaText) {
        final SpecVersion.VersionFlag selected = selectedVersion(schemaText);
        return JsonSchemaFactory.getInstance(selected);
    }

    public SpecVersion.VersionFlag selectedVersion(final String schemaText) {
        final List<SpecVersion.VersionFlag> candidates = detectCandidates(schemaText);
        for (SpecVersion.VersionFlag v : candidates) {
            try {
                final JsonSchemaFactory f = JsonSchemaFactory.getInstance(v);
                f.getSchema(schemaText);
                return v;
            } catch (Exception ignored) {
            }
        }
        return SpecVersion.VersionFlag.V4;
    }
}
