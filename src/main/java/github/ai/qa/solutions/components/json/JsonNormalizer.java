package github.ai.qa.solutions.components.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.stereotype.Component;

/** Normalizes textual values across a JSON tree. */
@Component
public class JsonNormalizer {
    /** Jackson mapper used for node creation. */
    private final ObjectMapper objectMapper;

    /**
     * Creates a normalizer bound to the given mapper.
     *
     * @param objectMapper Jackson mapper for nodes
     */
    public JsonNormalizer(final ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Returns a new tree where all textual values are normalized.
     *
     * @param root input root node
     * @return normalized tree (same instance types as input)
     */
    public JsonNode normalize(final JsonNode root) {
        if (root == null || root.isNull()) return root;
        if (root.isTextual()) {
            return TextNode.valueOf(normalizeString(root.asText()));
        }
        if (root.isObject()) {
            final ObjectNode on = root.deepCopy();
            on.fieldNames().forEachRemaining(k -> on.set(k, normalize(on.get(k))));
            return on;
        }
        if (root.isArray()) {
            final ArrayNode an = objectMapper.createArrayNode();
            for (int i = 0; i < root.size(); i++) {
                an.add(normalize(root.get(i)));
            }
            return an;
        }
        return root;
    }

    /**
     * Applies textual normalization: trims Unicode/ASCII spaces, unifies dashes,
     * converts full-width digits, collapses non-breaking spaces to regular space,
     * and fixes specific phone-like patterns.
     *
     * @param s input string; may be null
     * @return normalized string, or null when input is null
     */
    public String normalizeString(String s) {
        if (s == null) return null;
        String out = s;
        out = trimUnicode(out);
        out = out.replaceAll("[\\u2010\\u2011\\u2012\\u2013\\u2014\\u2015\\u2212]", "-");
        out = out.replaceAll("[\\u00A0\\u2007\\u202F]", " ");
        out = normalizeFullWidthDigits(out);
        return out;
    }

    /**
     * Trims ASCII and common Unicode spaces (NBSP/NNBSP/thin space) from both ends.
     *
     * @param s input string
     * @return string without leading/trailing Unicode spaces
     */
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

    /**
     * Checks whether a code point should be treated as trimmable whitespace.
     *
     * @param codePoint Unicode code point
     * @return true when considered whitespace for trimming
     */
    private boolean isTrimChar(int codePoint) {
        return Character.isWhitespace(codePoint) || codePoint == 0x00A0 || codePoint == 0x2007 || codePoint == 0x202F;
    }

    /**
     * Converts full-width digits (\uFF10–\uFF19) to ASCII digits (0–9).
     *
     * @param s input string
     * @return string where full-width digits are converted to ASCII
     */
    private static String normalizeFullWidthDigits(String s) {
        if (s == null || s.isEmpty()) return s;

        final StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            // U+FF10 ('０') .. U+FF19 ('９')
            if (ch >= 0xFF10 && ch <= 0xFF19) {
                sb.append((char) ('0' + (ch - 0xFF10)));
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
