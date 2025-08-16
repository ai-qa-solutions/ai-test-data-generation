package github.ai.qa.solutions.components.json;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/** Provides light text sanitization for model outputs. */
@Component
public class JsonOutputSanitizer {

    /**
     * Removes leading/trailing Markdown code fences, e.g. ```json ... ```.
     *
     * @param raw raw text possibly wrapped in fences
     * @return text without surrounding code fences
     */
    public String stripFences(final String raw) {
        if (raw == null) return null;
        String s = raw;
        if (StringUtils.startsWith(s, "```") && StringUtils.endsWith(s.trim(), "```")) {
            s = s.replaceAll("^```[a-zA-Z]*\\n|```$", "").trim();
        }
        return s;
    }
}
