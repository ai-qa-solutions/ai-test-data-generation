package github.ai.qa.solutions.configuration;

import lombok.AllArgsConstructor;
import org.bsc.langgraph4j.studio.springboot.AbstractLangGraphStudioConfig;
import org.bsc.langgraph4j.studio.springboot.LangGraphFlow;
import org.springframework.context.annotation.Configuration;

@Configuration
@AllArgsConstructor
public class LangGraphStudioConfiguration extends AbstractLangGraphStudioConfig {

    private LangGraphFlow langGraphFlow;

    @Override
    public LangGraphFlow getFlow() {
        return langGraphFlow;
    }
}
