package github.ai.qa.solutions.components.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

/**
 * Analyzes a JSON tree and reports values that look like placeholders
 * (e.g., "John Doe", "Иванов Иван Иванович", "12345", "012345", "987654321").
 *
 * <p>Public API is limited to {@link #analyze(String)}. Internal helpers and
 * patterns can evolve without breaking the public contract.</p>
 */
@Component
public class PlaceholderAnalyzer {

    /**
     * Marker words commonly used in test data (English/Russian).
     * <p>We emulate word boundaries using non-letter guards to be Unicode-friendly.</p>
     */
    private static final Pattern WORD_PLACEHOLDERS = Pattern.compile(
            "(?iu)(^|[^\\p{L}])(test|example|sample|dummy|foobar|password|пароль|пример|тест)([^\\p{L}]|$)");

    /**
     * Classic lorem ipsum filler text.
     */
    private static final Pattern LOREM_IPSUM = Pattern.compile("(?iu)lorem\\s+ipsum");

    /**
     * Common fake/full-name placeholders (English/Russian).
     * Guards avoid matching inside larger tokens.
     */
    private static final Pattern COMMON_NAMES = Pattern.compile("(?iu)(^|[^\\p{L}])"
            + "(john\\s+doe|jane\\s+doe|"
            + "ivan\\s+ivanov|ivanov\\s+ivan(\\s+ivanovich)?|"
            + "иванов\\s+иван(\\s+иванович)?|петров\\s+петр(\\s+петрович)?)"
            + "([^\\p{L}]|$)");

    /**
     * Placeholder-like emails: test@example.com, demo@localhost, etc.
     */
    private static final Pattern EMAIL_PLACEHOLDER = Pattern.compile(
            "(?iu)\\b(test|example|demo|sample|dummy|admin|user|foo|bar)@(?:example\\.(?:com|org|net)|test\\.(?:com|org|net)|localhost)\\b");

    /**
     * Phone-like placeholders: 123-456, 000-000, 555-01xx (US samples), repeated digits.
     */
    private static final Pattern PHONE_PLACEHOLDER =
            Pattern.compile("(?iu)(\\b123[-\\s]?456\\b|\\b000[-\\s]?000\\b|\\b555[-\\s]?01\\d{2}\\b)");

    /**
     * Canonical numeric runs often used in fake data.
     */
    private static final Set<String> CANONICAL_NUM_RUNS = Set.of(
            "1234",
            "012345",
            "12345",
            "123456",
            "987654321",
            "0000",
            "1111",
            "2222",
            "3333",
            "4444",
            "5555",
            "6666",
            "7777",
            "8888",
            "9999");

    /**
     * Finds digit-only substrings to inspect each run separately.
     */
    private static final Pattern DIGIT_RUN = Pattern.compile("\\d+");

    private final ObjectMapper objectMapper;

    public PlaceholderAnalyzer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Traverses the given JSON tree and collects warning messages for suspicious values.
     *
     * @param json the (possibly normalized) JSON node to analyze; {@code null} is treated as empty
     * @return immutable list of warning strings, each including the JSON-like path and a short preview
     */
    @SneakyThrows
    public List<String> analyze(final String json) {
        final JsonNode jsonNode = objectMapper.readValue(json, JsonNode.class);
        final List<String> warnings = new ArrayList<>();
        walk(jsonNode, "$", warnings);
        return warnings;
    }

    /**
     * Recursively walks the JSON structure while building a pointer-like path.
     *
     * @param node     current node (object/array/scalar)
     * @param path     current JSON-like path (e.g., {@code $/user/name} or {@code $/items[0]})
     * @param warnings output collector for warnings
     */
    private void walk(final JsonNode node, final String path, final List<String> warnings) {
        if (node == null || node.isNull()) return;

        if (node.isTextual() || node.isNumber()) {
            final String raw = node.asText();
            if (isSuspiciousPlaceholder(raw)) {
                addWarning(warnings, path, raw);
            }
            return;
        }

        if (node.isObject()) {
            final ObjectNode on = (ObjectNode) node;
            final Iterator<Map.Entry<String, JsonNode>> it = on.fields();
            while (it.hasNext()) {
                final Map.Entry<String, JsonNode> e = it.next();
                walk(e.getValue(), path + "/" + e.getKey(), warnings);
            }
            return;
        }

        if (node.isArray()) {
            final ArrayNode an = (ArrayNode) node;
            for (int i = 0; i < an.size(); i++) {
                walk(an.get(i), path + "[" + i + "]", warnings);
            }
        }
    }

    /**
     * Returns {@code true} if the given string looks like a placeholder.
     *
     * <p>The decision is heuristic and considers:
     * <ul>
     *   <li>marker words (test/example/тест/пример, etc.),</li>
     *   <li>common fake names (John/Jane Doe, Иванов Иван Иванович, ...),</li>
     *   <li>placeholder emails (e.g., {@code test@example.com}),</li>
     *   <li>placeholder-like phones (e.g., {@code 123-456}, {@code 000-000}, {@code 555-01xx}),</li>
     *   <li>numeric runs such as {@code 1234}, {@code 012345}, {@code 987654321}, long monotonic or all-equal digits.</li>
     * </ul>
     * Normalization uses NFKC and explicit full-width digit folding.</p>
     *
     * @param s candidate value (maybe {@code null})
     * @return {@code true} if the value is likely a placeholder; {@code false} otherwise
     */
    private boolean isSuspiciousPlaceholder(final String s) {
        if (s == null) return false;

        final String norm = normalizeForMatch(s);
        if (norm.isEmpty()) return false;

        if (WORD_PLACEHOLDERS.matcher(norm).find()) return true;
        if (LOREM_IPSUM.matcher(norm).find()) return true;
        if (COMMON_NAMES.matcher(norm).find()) return true;
        if (EMAIL_PLACEHOLDER.matcher(norm).find()) return true;
        if (PHONE_PLACEHOLDER.matcher(norm).find()) return true;

        return looksLikeNumericPlaceholder(norm);
    }

    /**
     * Scans all digit runs within the string and flags:
     * <ul>
     *   <li>all-equal digits (e.g., {@code 0000}, {@code 111}),</li>
     *   <li>canonical runs from {@link #CANONICAL_NUM_RUNS},</li>
     *   <li>strictly monotonic ascending/descending runs of length ≥ 4.</li>
     * </ul>
     *
     * @param norm lowercased, normalized string
     * @return {@code true} if any suspicious pattern is found
     */
    private boolean looksLikeNumericPlaceholder(final String norm) {
        final Matcher m = DIGIT_RUN.matcher(norm);
        while (m.find()) {
            final String run = m.group();
            if (run.length() >= 3 && allSame(run)) return true;
            if (CANONICAL_NUM_RUNS.contains(run)) return true;
            if (run.length() >= 4 && (isMonotonic(run, true) || isMonotonic(run, false))) return true;
        }
        return false;
    }

    /**
     * Normalizes a string for pattern matching: trims, applies NFKC, folds full-width digits to ASCII,
     * and lowercases the result.
     *
     * @param s input string
     * @return normalized string ready for regex checks
     */
    private String normalizeForMatch(final String s) {
        String v = s.trim();
        if (v.isEmpty()) return v;
        v = Normalizer.normalize(v, Form.NFKC);
        v = normalizeFullWidthDigits(v);
        return v.toLowerCase();
    }

    /**
     * Converts full-width digits (U+FF10–U+FF19) to ASCII digits (0–9).
     *
     * @param s input string
     * @return string with full-width digits folded to ASCII
     */
    private String normalizeFullWidthDigits(String s) {
        if (s == null || s.isEmpty()) return s;
        final StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= 0xFF10 && ch <= 0xFF19) { // '０'..'９'
                sb.append((char) ('0' + (ch - 0xFF10)));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * Returns {@code true} if all characters in {@code digits} are identical.
     *
     * @param digits digit-only string
     * @return {@code true} if all chars are the same
     */
    private static boolean allSame(final String digits) {
        for (int i = 1; i < digits.length(); i++) {
            if (digits.charAt(i) != digits.charAt(0)) return false;
        }
        return true;
    }

    /**
     * Checks if the entire digit run is strictly monotonic (+1 or -1 per step).
     * Examples: {@code 012345}, {@code 1234}, {@code 987654321}.
     *
     * @param digits digit-only string
     * @param asc    {@code true} for ascending, {@code false} for descending
     * @return {@code true} if the run is strictly monotonic and length ≥ 4
     */
    private static boolean isMonotonic(final String digits, final boolean asc) {
        int run = 1;
        for (int i = 1; i < digits.length(); i++) {
            int prev = digits.charAt(i - 1) - '0';
            int cur = digits.charAt(i) - '0';
            if (asc) {
                if (cur == prev + 1) run++;
                else return false;
            } else {
                if (cur == prev - 1) run++;
                else return false;
            }
        }
        return run >= 4;
    }

    /**
     * Adds a formatted warning line with a shortened, single-line preview of the value.
     *
     * @param warnings collector
     * @param path     JSON-like path
     * @param rawValue original value
     */
    private static void addWarning(final List<String> warnings, final String path, final String rawValue) {
        warnings.add(path + ": suspicious placeholder-like value: '" + preview(rawValue) + "'");
    }

    /**
     * Returns a single-line preview of {@code v}, escaping newlines/tabs and truncating long values.
     *
     * @param v original value
     * @return shortened preview
     */
    private static String preview(final String v) {
        final String flat = v.replace("\n", "\\n").replace("\t", "\\t");
        return (flat.length() > 120) ? flat.substring(0, 117) + "..." : flat;
    }
}
