package github.ai.qa.solutions.services;

import org.springframework.ai.chat.client.ChatClient;

public interface ChatClientRouter {
    ChatClient forNode(String nodeOrToolSimpleName);
}
