package github.ai.qa.solutions.configuration;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

/**
 * Central configuration for wiring {@link ChatClient} beans based on Spring profiles.
 * <p>
 * Supported profiles:
 * <ul>
 *   <li><b>gigachat-only</b>: both generative & thinking clients use GigaChat</li>
 *   <li><b>openrouter-only</b>: both generative & thinking clients use OpenRouter (OpenAI-compatible)</li>
 *   <li><b>gigachat-openrouter</b>: generative uses GigaChat, thinking uses OpenRouter</li>
 * </ul>
 * <p>
 * This layout follows modern Spring Boot guidance:
 * <ul>
 *   <li>Use nested configuration classes per profile (clear separation, easy to scan).</li>
 *   <li>{@code proxyBeanMethods = false} for leaner configuration.</li>
 *   <li>Fail-fast by requiring the expected model beans via explicit qualifiers.</li>
 *   <li>Expose a default {@link ChatClient} that uses the {@code @Primary} {@link ChatModel}.</li>
 *   <li>Keep routing properties in a strongly-typed {@code @ConfigurationProperties} class.</li>
 * </ul>
 * <p>
 * Assumptions:
 * <ul>
 *   <li>The Spring AI starters create model beans named <b>openAiChatModel</b> and <b>gigaChatChatModel</b>.</li>
 *   <li>You're on Spring Boot 3.5.4 (Java 17+).</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiClientsConfiguration.NodeModelRoutingProperties.class)
public class AiClientsConfiguration {

    /**
     * Uses the @Primary ChatModel decided by the active profile (see below).
     * Default ChatClient using the Primary ChatModel (helpful fallback)
     *
     * @param primaryChatModel default primary chat model
     * @return fallback ChatClient if no ready to use
     */
    @Bean
    @ConditionalOnMissingBean(ChatClient.class)
    public ChatClient chatClient(final ChatModel primaryChatModel) {
        // Uses the @Primary ChatModel decided by the active profile (see below)
        return ChatClient.create(primaryChatModel);
    }

    // --------------------------------------------------------------
    // Primary ChatModel selection per profile
    // --------------------------------------------------------------
    @Bean
    @Primary
    @Profile({"gigachat-openrouter", "gigachat-only"})
    public ChatModel primaryGigaChatModel(@Qualifier("gigaChatChatModel") final ChatModel gigaChatChatModel) {
        return gigaChatChatModel;
    }

    @Bean
    @Primary
    @Profile("openrouter-only")
    public ChatModel primaryOpenRouterModel(@Qualifier("openAiChatModel") final OpenAiChatModel openAiChatModel) {
        return openAiChatModel;
    }

    // --------------------------------------------------------------
    // Profile-specific wiring
    // --------------------------------------------------------------

    /**
     * gigachat-only: both clients -> GigaChat
     */
    @Configuration
    @Profile("gigachat-only")
    static class GigaChatOnlyConfig {

        @Bean
        @Qualifier("generativeChatClient")
        public ChatClient generativeChatClient(@Qualifier("gigaChatChatModel") final ChatModel giga) {
            return ChatClient.create(giga);
        }

        @Bean
        @Qualifier("thinkingChatClient")
        public ChatClient thinkingChatClient(@Qualifier("gigaChatChatModel") final ChatModel giga) {
            return ChatClient.create(giga);
        }
    }

    /**
     * openrouter-only: both clients -> OpenRouter
     */
    @Configuration
    @Profile("openrouter-only")
    static class OpenRouterOnlyConfig {

        @Bean
        @Qualifier("generativeChatClient")
        public ChatClient generativeChatClient(@Qualifier("openAiChatModel") final OpenAiChatModel openRouter) {
            return ChatClient.create(openRouter);
        }

        @Bean
        @Qualifier("thinkingChatClient")
        public ChatClient thinkingChatClient(@Qualifier("openAiChatModel") final OpenAiChatModel openRouter) {
            return ChatClient.create(openRouter);
        }
    }

    /**
     * gigachat-openrouter: generative -> GigaChat, thinking -> OpenRouter
     */
    @Configuration
    @Profile("gigachat-openrouter")
    static class MixedConfig {

        @Bean
        @Qualifier("generativeChatClient")
        public ChatClient generativeChatClient(@Qualifier("gigaChatChatModel") final ChatModel giga) {
            return ChatClient.create(giga);
        }

        @Bean
        @Qualifier("thinkingChatClient")
        public ChatClient thinkingChatClient(@Qualifier("openAiChatModel") final OpenAiChatModel openRouter) {
            return ChatClient.create(openRouter);
        }
    }

    // --------------------------------------------------------------
    // Strongly-typed routing properties for node -> model mapping
    // --------------------------------------------------------------

    @Setter
    @Getter
    @ConfigurationProperties(prefix = "ai.model-routing")
    public static class NodeModelRoutingProperties {
        /**
         * Map of NodeSimpleName -> model name as understood by the provider.
         * Example:
         *   ReasonAndRouteNode: deepseek/deepseek-r1
         *   GenerateJsonBySchemaTool: GigaChat-2-Max
         */
        private Map<String, String> nodes = new HashMap<>();
    }
}
