package github.ai.qa.solutions.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.SpecVersion;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Epic("AI Test Data Generation")
@Feature("Schema Version Detection")
@Owner("repo-maintainers")
@Tag("unit")
class SchemaVersionDetectorTest {
    private final SchemaVersionDetector detector = new SchemaVersionDetector(new ObjectMapper());

    @Test
    @Story("Detect explicit $schema URL")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Detects 2020-12 via $schema URL")
    void detectsExplicit2020() {
        String schema = "{\n  \"$schema\": \"https://json-schema.org/draft/2020-12/schema\"\n}";
        // step: run detector against schema with explicit 2020-12 meta
        assertEquals(SpecVersion.VersionFlag.V202012, detector.detectVersion(schema));
    }

    @Test
    @Story("Heuristic when $schema missing")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Detects 2019-09 via $defs heuristic")
    void detects2019ByDefsHeuristicWhenMissingMeta() {
        String schema = "{\n  \"$defs\": {\n    \"x\": { \"type\": \"string\" }\n  }\n}";
        // step: run detector without $schema but with $defs
        assertEquals(SpecVersion.VersionFlag.V201909, detector.detectVersion(schema));
    }

    @Test
    @Story("Fallback behavior")
    @Severity(SeverityLevel.MINOR)
    @DisplayName("Defaults to V4 when unknown")
    void defaultsToV4() {
        String schema = "{\n  \"type\": \"object\"\n}";
        // step: run detector against minimal schema without hints
        assertEquals(SpecVersion.VersionFlag.V4, detector.detectVersion(schema));
    }
}
