package github.ai.qa.solutions.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Detects JSON Schema draft versions and provides factory fallbacks.
 *
 * @param objectMapper Jackson mapper used for lightweight schema parsing
 */
@Component
public record SchemaVersionDetector(ObjectMapper objectMapper) {

    /**
     * Detects the most likely JSON Schema draft version.
     *
     * <p>Strategy:
     * - Respect explicit $schema meta if present and recognizable.
     * - Otherwise use lightweight heuristics based on known keywords.
     * - Fall back to Draft 4 conservatively.
     *
     * @param schemaText raw JSON Schema string
     * @return detected version flag (never null)
     */
    public SpecVersion.VersionFlag detectVersion(final String schemaText) {
        try {
            final JsonNode root = objectMapper.readTree(schemaText);
            final String schemaUrl = root.path("$schema").asText("");
            if (!schemaUrl.isEmpty()) {
                final SpecVersion.VersionFlag byMeta = versionFromSchemaUri(schemaUrl);
                if (byMeta != null) {
                    return byMeta;
                }
            }
            // Heuristics when $schema is absent or unrecognized
            final String text = root.toString();
            if (contains(text, "\"prefixItems\"")) {
                // Introduced in 2020-12
                return SpecVersion.VersionFlag.V202012;
            }
            if (contains(text, "\"$defs\"")
                    || contains(text, "\"unevaluatedProperties\"")
                    || contains(text, "\"unevaluatedItems\"")
                    || contains(text, "\"dependentRequired\"")
                    || contains(text, "\"dependentSchemas\"")) {
                // Keywords standardized since 2019-09
                return SpecVersion.VersionFlag.V201909;
            }
        } catch (Exception ignored) {
            // If schema cannot be parsed, choose safe default below.
        }
        // Conservative default that most validators accept
        return SpecVersion.VersionFlag.V4;
    }

    /**
     * Returns an ordered list of candidate versions to try, starting from the primary guess.
     * Newer drafts are tried before older ones.
     *
     * @param schemaText raw JSON Schema string
     * @return ordered unique list of candidate versions
     */
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
            if (all.contains(v)) {
                order.add(v);
            }
        }
        return order;
    }

    /**
     * Returns a factory configured for a workable draft version.
     *
     * @param schemaText raw JSON Schema string
     * @return JsonSchemaFactory bound to a compatible draft
     */
    public JsonSchemaFactory factoryWithFallback(final String schemaText) {
        final SpecVersion.VersionFlag selected = selectedVersion(schemaText);
        return JsonSchemaFactory.getInstance(selected);
    }

    /**
     * Picks the first draft version that successfully compiles the schema using the
     * candidate order from {@link #detectCandidates(String)}.
     *
     * @param schemaText raw JSON Schema string
     * @return the first compatible version flag found, falling back to V4
     */
    public SpecVersion.VersionFlag selectedVersion(final String schemaText) {
        final List<SpecVersion.VersionFlag> candidates = detectCandidates(schemaText);
        for (SpecVersion.VersionFlag v : candidates) {
            try {
                final JsonSchemaFactory f = JsonSchemaFactory.getInstance(v);
                f.getSchema(schemaText);
                return v;
            } catch (Exception ignored) {
                // Try the next candidate
            }
        }
        return SpecVersion.VersionFlag.V4;
    }

    /**
     * Returns true if the given JSON text contains the token.
     *
     * @param text JSON text to scan
     * @param token token to search for
     * @return true if token is present
     */
    private static boolean contains(final String text, final String token) {
        return text != null && text.contains(token);
    }

    /**
     * Maps a $schema URI string (any case) to a known version flag.
     *
     * @param schemaUrl value of the $schema meta
     * @return version flag if recognized, otherwise null
     */
    private static SpecVersion.VersionFlag versionFromSchemaUri(final String schemaUrl) {
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
        return null;
    }
}
