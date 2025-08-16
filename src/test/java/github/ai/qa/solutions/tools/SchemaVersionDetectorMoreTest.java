package github.ai.qa.solutions.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Owner;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Story;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Epic("AI Test Data Generation")
@Feature("Schema Version Detection")
@Owner("repo-maintainers")
@Tag("unit")
class SchemaVersionDetectorMoreTest {
    private final SchemaVersionDetector detector = new SchemaVersionDetector(new ObjectMapper());

    @Test
    @Story("Candidate ordering")
    @Severity(SeverityLevel.MINOR)
    @DisplayName("Puts detected version first, then newer to older")
    void candidatesOrderAndUnique() {
        String schema = "{\n  \"$schema\": \"https://json-schema.org/draft/2019-09/schema\"\n}";
        List<SpecVersion.VersionFlag> expected = List.of(
                SpecVersion.VersionFlag.V201909,
                SpecVersion.VersionFlag.V202012,
                SpecVersion.VersionFlag.V7,
                SpecVersion.VersionFlag.V6,
                SpecVersion.VersionFlag.V4);
        assertEquals(expected, detector.detectCandidates(schema));
    }

    @Test
    @Story("Selection by compilation")
    @Severity(SeverityLevel.NORMAL)
    @DisplayName("Selects 2020-12 for prefixItems without $schema")
    void selects202012ForPrefixItems() {
        String schema = "{\n" + "  \"type\": \"array\",\n"
                + "  \"prefixItems\": [ { \"type\": \"string\" }, { \"type\": \"number\" } ],\n"
                + "  \"items\": false\n"
                + "}";
        assertEquals(SpecVersion.VersionFlag.V202012, detector.selectedVersion(schema));
        JsonSchemaFactory f = detector.factoryWithFallback(schema);
        assertNotNull(f.getSchema(schema));
    }

    @Test
    @Story("Factory fallback")
    @Severity(SeverityLevel.MINOR)
    @DisplayName("Returns usable factory for draft-07")
    void factoryCompilesDraft7() {
        String schema =
                "{\n  \"$schema\": \"http://json-schema.org/draft-07/schema#\",\n  \"type\": \"object\",\n  \"properties\": { \"name\": { \"type\": \"string\" } }\n}";
        JsonSchemaFactory f = detector.factoryWithFallback(schema);
        assertNotNull(f.getSchema(schema));
    }
}
