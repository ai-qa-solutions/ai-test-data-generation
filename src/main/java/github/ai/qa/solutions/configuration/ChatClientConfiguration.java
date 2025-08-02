package github.ai.qa.solutions.configuration;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfiguration {

    @Bean
    public ChatClient chatClient(final ChatClient.Builder builder) {
        return builder.build();
    }
}
