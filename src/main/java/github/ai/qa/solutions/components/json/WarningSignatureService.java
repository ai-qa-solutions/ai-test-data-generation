package github.ai.qa.solutions.components.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

/** Builds deterministic signatures from warning sets. */
@Component
public class WarningSignatureService {

    /**
     * Builds a stable signature by sorting warnings and joining with '|'.
     *
     * @param warnings list of warning messages
     * @return deterministic signature, or empty string when none
     */
    public String signature(final List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) return "";
        final List<String> copy = new ArrayList<>(warnings);
        Collections.sort(copy);
        return String.join("|", copy);
    }
}
