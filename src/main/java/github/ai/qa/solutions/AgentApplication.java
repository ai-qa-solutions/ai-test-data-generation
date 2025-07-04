package github.ai.qa.solutions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.EdgeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.studio.springboot.AbstractLangGraphStudioConfig;
import org.bsc.langgraph4j.studio.springboot.LangGraphFlow;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static github.ai.qa.solutions.AgentApplication.JsonAgentState.StateKey.*;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
@SpringBootApplication
public class AgentApplication {

    @Configuration
    @AllArgsConstructor
    public static class LangGraphStudioSampleConfig extends AbstractLangGraphStudioConfig {

        final ApplicationContext context;

        @Override
        public LangGraphFlow getFlow() {
            return context.getBean(LangGraphFlow.class);
        }

        @Bean
        public AiGenerationTools aiGenerationTools(final ChatClient.Builder builder) {
            return new AiGenerationTools(builder.build());
        }

        @Bean
        public GenerateNode generateNode(final AiGenerationTools aiGenerationTools) {
            return new GenerateNode(aiGenerationTools);
        }

        @Bean
        public VerifyNode verifyNode(final AiGenerationTools aiGenerationTools) {
            return new VerifyNode(aiGenerationTools);
        }

        @Bean
        public FixNode fixNode(final AiGenerationTools aiGenerationTools) {
            return new FixNode(aiGenerationTools);
        }

        @Bean
        public GenerationThinkNode thinkNode(final AiGenerationTools aiGenerationTools) {
            return new GenerationThinkNode(aiGenerationTools);
        }

        @Bean
        public ValidateSchemaNode validateSchemaNode(final ObjectMapper objectMapper) {
            return new ValidateSchemaNode(objectMapper);
        }

        @Bean
        public ReGenerationThinkNode reGenerationThinkNode(final AiGenerationTools aiGenerationTools) {
            return new ReGenerationThinkNode(aiGenerationTools);
        }

        @Bean
        @SneakyThrows
        public StateGraph<JsonAgentState> stateGraph(
                final ValidateSchemaNode validateSchemaNode,
                final GenerateNode generateNode,
                final VerifyNode verifyNode,
                final FixNode fixNode,
                final GenerationThinkNode generationThinkNode,
                final ReGenerationThinkNode reGenerationThinkNode) {
            final String FIXED = "ok";
            final String VALIDATION_ERROR = "validation_error";

            final String THINK = "think";
            final String THINK_AGAIN = "think_again";
            final String GENERATE = "generate";
            final String VERIFY = "verify";
            final String VALIDATE_SCHEMA = "validate_schema";
            final String FIX = "fix";
            final EdgeAction<JsonAgentState> isOk =
                    state -> state.get(VALIDATION_RESULT).equals("OK") ? FIXED : VALIDATION_ERROR;

            return new StateGraph<>(JsonAgentState.SCHEMA, JsonAgentState::new)
                    .addNode(VALIDATE_SCHEMA, node_async(validateSchemaNode))
                    .addNode(THINK, node_async(generationThinkNode))
                    .addNode(GENERATE, node_async(generateNode))
                    .addNode(VERIFY, node_async(verifyNode))
                    .addNode(FIX, node_async(fixNode))
                    .addNode(THINK_AGAIN, node_async(reGenerationThinkNode))

                    .addEdge(START, VALIDATE_SCHEMA)
                    .addEdge(VALIDATE_SCHEMA, THINK)
                    .addEdge(THINK, GENERATE)
                    .addEdge(GENERATE, VERIFY)
                    .addConditionalEdges(
                            VERIFY,
                            AsyncEdgeAction.edge_async(isOk),
                            Map.of(
                                    FIXED, END,
                                    VALIDATION_ERROR, THINK_AGAIN)
                    )
                    .addEdge(THINK_AGAIN, FIX)
                    .addEdge(FIX, VERIFY);
        }

        @Bean
        public LangGraphFlow langGraphFlow(final StateGraph<JsonAgentState> stateGraph) {
            return LangGraphFlow.builder()
                    .title("AI Test data generation")
                    .stateGraph(stateGraph)
                    .addInputStringArg(USER_PROMPT.name())
                    .addInputStringArg(JSON_SCHEMA.name())
                    .build();
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }

    public static class JsonAgentState extends AgentState {

        public JsonAgentState(Map<String, Object> initData) {
            super(initData);
        }

        public enum StateKey {
            USER_PROMPT,
            GENERATED_JSON,
            VALIDATION_RESULT,
            JSON_SCHEMA,
            GENERATION_THINK,
            RE_GENERATION_THINK
        }

        private static final Map<String, Channel<?>> SCHEMA = initSchema();

        private static Map<String, Channel<?>> initSchema() {
            final Map<String, Channel<?>> schemaMap = new HashMap<>();
            final Channel<?> defaultChannel = Channels.base((current, update) -> update);
            for (StateKey key : StateKey.values()) {
                schemaMap.put(key.name(), defaultChannel);
            }
            return schemaMap;
        }

        public String get(final StateKey key) {
            return this.<String>value(key.name())
                    .orElseThrow(() -> new IllegalStateException("Key did not found into AgentState: " + key));
        }
    }

    @AllArgsConstructor
    public static class ValidateSchemaNode implements NodeAction<JsonAgentState> {
        private final ObjectMapper objectMapper;

        @Override
        @SneakyThrows
        public Map<String, Object> apply(final JsonAgentState state) {
            final String jsonSchema = state.get(JSON_SCHEMA);
            try {
                JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4).getSchema(jsonSchema);
                final JsonNode jsonNode = objectMapper.readTree(jsonSchema);
                final String compactSchema = objectMapper.writeValueAsString(jsonNode)
                        .replace("\n", "").replace("\t", "");
                return Map.of(JSON_SCHEMA.name(), compactSchema);
            } catch (Exception e) {
                throw new GraphStateException("Incorrect jsonSchema:" + e.getMessage());
            }
        }
    }

    @AllArgsConstructor
    public static class GenerateNode implements NodeAction<JsonAgentState> {
        private final AiGenerationTools aiGenerationTools;

        @Override
        public Map<String, Object> apply(final JsonAgentState state) {
            final String generatedJson = aiGenerationTools.generateJsonBySchema(
                    state.get(USER_PROMPT),
                    state.get(JSON_SCHEMA),
                    state.get(GENERATION_THINK));
            return Map.of(GENERATED_JSON.name(), generatedJson);
        }
    }

    @AllArgsConstructor
    public static class VerifyNode implements NodeAction<JsonAgentState> {
        private final AiGenerationTools aiGenerationTools;

        @Override
        public Map<String, Object> apply(final JsonAgentState state) {
            final String json = state.get(GENERATED_JSON);
            final String schema = state.get(JSON_SCHEMA);
            final String result = aiGenerationTools.validateJsonBySchema(json, schema);
            return Map.of(VALIDATION_RESULT.name(), result);
        }
    }

    @AllArgsConstructor
    public static class FixNode implements NodeAction<JsonAgentState> {
        private final AiGenerationTools aiGenerationTools;

        @Override
        public Map<String, Object> apply(final JsonAgentState state) {
            final String json = state.get(GENERATED_JSON);
            final String schema = state.get(JSON_SCHEMA);
            final String errors = state.get(VALIDATION_RESULT);
            final String recommendation = state.get(RE_GENERATION_THINK);
            final String fixedJson = aiGenerationTools.fixJsonByErrorsAndSchema(errors, json, schema, recommendation);
            return Map.of(GENERATED_JSON.name(), fixedJson);
        }
    }

    @AllArgsConstructor
    public static class GenerationThinkNode implements NodeAction<JsonAgentState> {
        private final AiGenerationTools aiGenerationTools;

        @Override
        public Map<String, Object> apply(final JsonAgentState state) {
            final String schema = state.get(JSON_SCHEMA);
            final String userSpecificPromt = state.get(USER_PROMPT);
            final String thought = aiGenerationTools.thinkWhatToFill(schema, userSpecificPromt);
            return Map.of(GENERATION_THINK.name(), thought);
        }
    }

    @AllArgsConstructor
    public static class ReGenerationThinkNode implements NodeAction<JsonAgentState> {
        private final AiGenerationTools aiGenerationTools;

        @Override
        public Map<String, Object> apply(final JsonAgentState state) {
            final String errors = state.get(VALIDATION_RESULT);
            final String userSpecificPromt = state.get(USER_PROMPT);
            final String thought = aiGenerationTools.thinkHowToFix(errors, userSpecificPromt);
            return Map.of(RE_GENERATION_THINK.name(), thought);
        }
    }

    @AllArgsConstructor
    public static class AiGenerationTools {
        private final ChatClient chatClient;

        @Tool(
                name = "generateJsonFromSchema",
                description = "Generates RFC8259-compliant JSON test data that strictly adheres to JSON schema " +
                        "constraints while incorporating test-specific requirements and generation recommendations."
        )
        public String generateJsonBySchema(
                @ToolParam(description = "Test-specific constraints for data generation " +
                        "(language/region/format requirements)") final String userSpecificPromt,
                @ToolParam(description = "JSON schema defining data structure, " +
                        "validation rules, and field constraints") final String jsonSchema,
                @ToolParam(description = "Structured generation recommendations " +
                        "(output from generateTestDataPlan)") final String recommendation
        ) {
            return chatClient.prompt(
                            """
                                    Generate test data EXCLUSIVELY as JSON that strictly adheres to the JSON Schema.
                                    
                                    ### Test Scenario Specifications:
                                    %s
                                    
                                    ### Data Generation Recommendations:
                                    %s
                                    
                                    Your response should be in JSON format.
                                    Do not include any explanations, only provide a RFC8259 compliant
                                    JSON response following this format without deviation.
                                    Do not include markdown code blocks in your response.
                                    Remove the ```json markdown from the output.
                                    
                                    ### JSON Schema to Implement:
                                    %s
                                    """.formatted(userSpecificPromt, recommendation, jsonSchema)
                    )
                    .system("""
                            You are a precise JSON generator.
                            Follow these rules:
                            1. Always validate generated data against the provided JSON Schema.
                            2. Language Detection:
                               - Infer the target language from the schema’s example values
                               (if any) or the user’s prompt.
                               - Default to English unless Russian-specific terms
                               (e.g., "паспорт", "ФИО") or schema examples explicitly require Russian data.
                            3. For integers: avoid underscores (`_`) and ensure numeric validity.
                            4. For regex-defined fields: decode the regex first
                            (e.g., `^[A-Z]{2}\\d{6}$` → 2 uppercase letters + 6 digits)
                            5. Never include markdown, explanations, or metadata—return **only** the JSON object.
                            6. Ensure deterministic output for identical inputs by strictly following the schema.
                            """
                    )
                    .call()
                    .content();
        }

        @Tool(
                name = "validateJsonAgainstSchema",
                description = "Validates JSON data against provided JSON schema. " +
                        "Returns 'OK' for valid data or a newline-delimited list of validation errors."
        )
        public String validateJsonBySchema(
                @ToolParam(description = "JSON data to validate") String jsonTestData,
                @ToolParam(description = "JSON schema defining validation rules") String jsonSchema
        ) {
            try {
                final Set<ValidationMessage> errors = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V4)
                        .getSchema(jsonSchema)
                        .validate(jsonTestData, InputFormat.JSON);
                if (errors.isEmpty()) {
                    return "OK";
                }
                return errors.stream()
                        .map(ValidationMessage::getMessage)
                        .collect(Collectors.joining(" \n"));
            } catch (Exception e) {
                return e.getMessage();
            }
        }

        @Tool(
                name = "fixJsonValidationErrors",
                description = "Corrects JSON validation errors by applying structured recommendations while strictly " +
                        "adhering to JSON schema constraints. Outputs RFC8259-compliant JSON."
        )
        public String fixJsonByErrorsAndSchema(
                @ToolParam(description = "Raw validation error messages from schema validation") String validationErrors,
                @ToolParam(description = "Original JSON data requiring validation fixes") String jsonTestData,
                @ToolParam(description = "JSON schema defining data structure and validation rules") String jsonSchema,
                @ToolParam(description = "Structured error correction recommendations") String recommendation
        ) {
            final String promt = """
                    Fix validation errors in the provided JSON data while strictly adhering to the JSON Schema.
                    
                    ### Recommendations for Corrections in json
                    %s
                    
                    ### Validation Errors to Fix:
                    %s
                    
                    ### Generated Test Data:
                    %s
                    
                    Your response should be in JSON format.
                    Do not include any explanations, only provide a RFC8259 compliant
                    JSON response following this format without deviation.
                    Do not include markdown code blocks in your response.
                    Remove the ```json markdown from the output.
                    
                    ### JSON Schema to Implement:
                    %s
                    """;
            return chatClient.prompt(promt.formatted(recommendation, validationErrors, jsonTestData, jsonSchema))
                    .system("""
                            You are a precise JSON validator and fixer. Follow these rules:
                            1. Error Resolution:
                               - Analyze the validation errors and modify only the conflicting fields.
                               - Retain valid fields from the input JSON unless they violate schema constraints.
                            2. Language Handling:
                               - Infer the target language from schema examples (e.g., "ФИО" → Russian) or
                               the user’s original prompt or the generated test data.
                               - Default to English unless Russian-specific terms (e.g., "паспорт", "ИНН" and etc)
                               are present in the schema or errors.
                            3. Schema Compliance:
                               - Validate all fields against the schema’s format, regex, and type requirements.
                               - For integers: avoid underscores (`_`) and ensure numeric validity.
                               - For regex-defined fields: decode and match the pattern strictly
                               (e.g., `^\\+7\\d{10}$` → Russian phone number format).
                            4. Output Rules:
                               - Return **only** the corrected JSON object.
                               - Never include markdown, explanations, or metadata.
                               - Ensure deterministic output for identical inputs by strictly following the schema.
                            """)
                    .call()
                    .content();
        }

        @Tool(
                name = "generateTestDataPlan",
                description = "Creates structured test data generation plan based on JSON schema constraints and test-specific requirements. Outputs step-by-step instructions."
        )
        public String thinkWhatToFill(
                @ToolParam(description = "Test-specific constraints for data generation " +
                        "(language/region/format requirements)") final String userPromt,
                @ToolParam(description = "JSON schema defining data structure, " +
                        "validation rules, and field constraints") final String jsonSchema
        ) {
            return chatClient.prompt("""
                            Analyze the test context and JSON schema to provide structured recommendations
                            for filling test data.
                            
                            ### Test Context:
                            %s
                            
                            ### JSON Schema:
                            %s
                            
                            Response Structure:
                            1. Required Fields: List all `required` fields and their purpose.
                            2. Regex Constraints: For each field with a `pattern`:
                               - Explain the regex (e.g., `^[A-Z]{2}\\d{6}$` → 2 uppercase letters + 6 digits).
                               - Provide valid examples (e.g., `AB123456`).
                            3. Other critical Constraints:
                               - Format rules (`date`, `email`, `phone`).
                               - Length/numeric ranges (`minLength`, `maxLength`, `minimum`, `maximum`).
                            4. Data Recommendations:
                               - Realistic values based on inferred language (e.g., English names vs. Russian ФИО).
                               - How to avoid validation errors (e.g., avoid `_` in integers).
                            
                            Rules:
                            - Do NOT provide full JSON examples.
                            - Base recommendations strictly on the schema and test context.
                            - Use Russian-specific data (e.g., ФИО, СНИЛС passport numbers) ONLY IF the schema or context
                            implies Russian usage (e.g., `ФИО` in examples).
                            """.formatted(userPromt, jsonSchema)
                    )
                    .system("""
                            You are a test data analyst specializing in JSON schema compliance.
                            
                            Follow these rules:
                            1. Schema Analysis:
                               - Identify required fields, regex patterns, data types, and format constraints.
                               - Prioritize constraints like `minLength`, `maxLength`, `minimum`, and `maximum`.
                            2. Language Handling:
                               - Infer the target language from schema examples
                                (e.g., `ФИО` → Russian) or test context.
                               - Default to English unless Russian-specific terms
                               (e.g., `паспорт`, `ИНН`) are present.
                            3. Data Precision:
                               - Always decode regex patterns before suggesting examples
                               (e.g., `^\\+7\\d{10}$` → Russian phone number).
                               - Avoid invalid characters (e.g., `_` in integers).
                            4. Output Focus:
                               - Provide ONLY structured recommendations (no explanations, code, or JSON examples).
                               - Ensure recommendations align with schema validity and test context.
                            """
                    )
                    .call()
                    .content();
        }

        @Tool(
                name = "thinkHowToFix",
                description = "Analyzes JSON schema validation errors and provides structured recommendations to fix them while adhering to test-specific constraints."
        )
        public String thinkHowToFix(
                @ToolParam(description = "Raw validation errors output from " +
                        "schema validation process") final String errors,
                @ToolParam(description = "Test-specific data generation constraints " +
                        "(language/region/format requirements)") final String userPromt
        ) {
            return chatClient.prompt("""
                            Analyze validation errors and provide structured recommendations to fix them while
                            aligning with the test context.
                            
                            Validation Errors to Fix:
                            %s
                            
                            Test Context (Constraints):
                            %s
                            
                            Response Structure:
                            For EACH ERROR, provide:
                            1. Error Description: Summarize the validation issue
                            (e.g., regex mismatch, type error).
                            2. Root Cause: Explain why the error occurred
                            (e.g., "Value violates regex `^[A-Z]{2}\\d{6}$`").
                            3. Fix Recommendation:
                               - How to correct the value
                               (e.g., "Use 2 uppercase letters + 6 digits like `AB123456` for this field").
                               - Reference schema constraints (regex, data type, min/max length).
                            4. Context Alignment: Ensure fixes comply with test-specific requirements
                            (e.g., use Russian language if implied by Test Context or validation errors).
                            
                            Rules:
                            - Do NOT provide full JSON examples.
                            - Prioritize fixes that resolve errors without introducing new violations.
                            - For regex errors, decode the pattern first (e.g.,`^\\+7\\d{10}$` → Russian phone format).
                            - Avoid invalid characters (e.g., `_` in integers).
                            """.formatted(errors, userPromt)
                    )
                    .system("""
                            You are a test data analyst specializing in JSON schema validation fixes.
                            
                            Follow these rules:
                            1. Error Analysis:
                               - Break down each validation error into root causes
                               (regex, type, required fields, min/max constraints).
                               - Prioritize fixes that resolve the error without violating other schema rules.
                            2. Language Handling:
                               - Use the same language as the test context or schema examples (default to English).
                               - Apply Russian-specific data (e.g., ФИО, passport numbers, etc) ONLY IF the test
                               context or errors imply Russian usage (e.g., `ФИО` in examples).
                            3. Precision:
                               - Always decode regex patterns before suggesting fixes
                               (e.g., `^[A-Z]{2}\\d{6}$` → 2 uppercase letters + 6 digits).
                               - Avoid invalid characters (e.g., `_` in integers).
                            4. Output Focus:
                               - Provide only structured recommendations (no explanations, code, or JSON examples).
                               - Align fixes with the test context
                               (e.g., use realistic Russian phone numbers if required).
                            """
                    )
                    .call()
                    .content();
        }
    }
}