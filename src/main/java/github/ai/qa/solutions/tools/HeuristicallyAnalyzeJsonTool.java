package github.ai.qa.solutions.tools;

import github.ai.qa.solutions.components.json.PlaceholderAnalyzer;
import github.ai.qa.solutions.components.json.WarningSignatureService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class HeuristicallyAnalyzeJsonTool {
    private final PlaceholderAnalyzer placeholderAnalyzer;
    private final WarningSignatureService warningSignatureService;

    public HeuristicallyAnalyzeJsonTool(
            final PlaceholderAnalyzer placeholderAnalyzer, final WarningSignatureService warningSignatureService) {
        this.placeholderAnalyzer = placeholderAnalyzer;
        this.warningSignatureService = warningSignatureService;
    }

    @Tool(
            name = "heuristicallyAnalyzeJson",
            description =
                    "Heuristically scans JSON for placeholder-like test data and returns a signed summary. Unicode-aware and reports JSON paths.")
    public String heuristicallyAnalyzeJson(
            @ToolParam(description = "Raw JSON text to analyze") final String inputJson) {
        log.info("🛠️ coded as tool 💻: HeuristicallyAnalyzeTool");
        final List<String> warnings = placeholderAnalyzer.analyze(inputJson);
        return warningSignatureService.signature(warnings);
    }
}
