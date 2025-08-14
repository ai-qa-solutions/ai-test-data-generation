package github.ai.qa.solutions.nodes;

import static github.ai.qa.solutions.state.AgentState.StateKey.GENERATED_JSON;
import static github.ai.qa.solutions.state.AgentState.StateKey.HEURISTIC_SIGNATURE;
import static github.ai.qa.solutions.state.AgentState.StateKey.HEURISTIC_WARNINGS;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import github.ai.qa.solutions.state.AgentState;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import org.bsc.langgraph4j.action.NodeAction;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class NormalizeGeneratedJsonNode implements NodeAction<AgentState> {

    private final ObjectMapper objectMapper;

    @Override
    @SneakyThrows
    public Map<String, Object> apply(final AgentState state) {
        String json = state.get(GENERATED_JSON);
        // Strip markdown fences if model returned fenced JSON
        if (json.startsWith("```")) {
            json = json.replaceAll("^```[a-zA-Z]*\\n|```$", "").trim();
        }
        final JsonNode root = objectMapper.readTree(json);
        final List<String> warnings = new ArrayList<>();
        final JsonNode normalized = normalizeNode(root, "$", warnings);
        final String result = objectMapper.writeValueAsString(normalized);
        final String warningsText = String.join(" \n", warnings);
        final String signature = makeSignature(warnings);
        return Map.of(
                GENERATED_JSON.name(), result,
                HEURISTIC_WARNINGS.name(), warningsText,
                HEURISTIC_SIGNATURE.name(), signature);
    }

    private JsonNode normalizeNode(JsonNode node, String path, List<String> warnings) {
        if (node == null || node.isNull()) return node;
        if (node.isTextual()) {
            final String s = node.asText();
            final String normalized = normalizeString(s);
            if (isSuspiciousPlaceholder(normalized)) {
                warnings.add(path + ": suspicious placeholder-like value: '" + normalized + "'");
            }
            return TextNode.valueOf(normalized);
        } else if (node.isObject()) {
            final ObjectNode on = (ObjectNode) node;
            final Iterator<Map.Entry<String, JsonNode>> it = on.fields();
            while (it.hasNext()) {
                final Map.Entry<String, JsonNode> e = it.next();
                on.set(e.getKey(), normalizeNode(e.getValue(), path + "/" + e.getKey(), warnings));
            }
            return on;
        } else if (node.isArray()) {
            final ArrayNode an = (ArrayNode) node;
            for (int i = 0; i < an.size(); i++) {
                an.set(i, normalizeNode(an.get(i), path + "[" + i + "]", warnings));
            }
            return an;
        }
        return node;
    }

    private String normalizeString(String s) {
        if (s == null) return null;
        String out = s;
        // Trim surrounding whitespace including non-breaking spaces
        out = trimUnicode(out);
        // Normalize Unicode dashes to ASCII hyphen-minus
        out = out.replaceAll("[\u2010\u2011\u2012\u2013\u2014\u2015\u2212]", "-");
        // Special-case: accidentally prefixed '+' for patterns like ddd-ddd (e.g., unit_code)
        out = out.replaceAll("^\\+(\\d{3}-\\d{3})$", "$1");
        // Replace various NBSPs with regular space
        out = out.replaceAll("[\u00A0\u2007\u202F]", " ");
        // Normalize full-width digits to ASCII
        out = normalizeFullWidthDigits(out);
        return out;
    }

    private String trimUnicode(String s) {
        int len = s.length();
        int st = 0;
        while (st < len) {
            int cp = s.codePointAt(st);
            if (!isTrimChar(cp)) break;
            st += Character.charCount(cp);
        }
        while (st < len) {
            int cp = s.codePointBefore(len);
            if (!isTrimChar(cp)) break;
            len -= Character.charCount(cp);
        }
        return s.substring(st, len);
    }

    private boolean isTrimChar(int codePoint) {
        return Character.isWhitespace(codePoint) || codePoint == 0x00A0 || codePoint == 0x2007 || codePoint == 0x202F;
    }

    private String normalizeFullWidthDigits(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '\uFF10' && ch <= '\uFF19') {
                sb.append((char) ('0' + (ch - '\uFF10')));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private boolean isSuspiciousPlaceholder(final String s) {
        final String v = s == null ? "" : s.trim();
        if (v.isEmpty()) return false;
        final String digits = v.replaceAll("\\D", "");
        if (digits.length() >= 5) {
            // all-equal digits
            boolean allEqual = true;
            for (int i = 1; i < digits.length(); i++) {
                if (digits.charAt(i) != digits.charAt(0)) {
                    allEqual = false;
                    break;
                }
            }
            if (allEqual) return true;
            // monotonic sequences length >=5
            if (isMonotonicSequence(digits, true) || isMonotonicSequence(digits, false)) return true;
        }
        // trivial groups
        if (v.matches("(?i).*\\b123-456\\b.*") || v.matches(".*\\b000-000\\b.*")) return true;
        // dummy words
        if (v.matches("(?i).*(^|\\b)(test|example|пример|тест)(\\b|$).*")) return true;
        // very overused Russian placeholder name
        if (v.equalsIgnoreCase("Иванов Иван Иванович")) return true;
        return false;
    }

    private boolean isMonotonicSequence(final String digits, final boolean asc) {
        if (digits == null || digits.length() < 5) return false;
        int run = 1;
        for (int i = 1; i < digits.length(); i++) {
            int prev = digits.charAt(i - 1) - '0';
            int cur = digits.charAt(i) - '0';
            if (asc) {
                if (cur == prev + 1) run++;
                else run = 1;
            } else {
                if (cur == prev - 1) run++;
                else run = 1;
            }
            if (run >= 5) return true;
        }
        return false;
    }

    private String makeSignature(final List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) return "";
        final List<String> copy = new ArrayList<>(warnings);
        Collections.sort(copy);
        return String.join("|", copy);
    }
}
