package dev.langchain4j.community.cohere.spring;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class TestChatModelListenerAutoConfiguration {

    @Bean
    ChatModelListener testChatModelListener() {
        return new ChatModelListener() {};
    }
}
