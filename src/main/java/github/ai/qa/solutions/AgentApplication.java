package github.ai.qa.solutions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgentApplication {

    private AgentApplication() {}

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
