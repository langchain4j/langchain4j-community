package dev.langchain4j.community.cohere.spring;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnMissingBean(ChatModelListener.class)
class TestChatModelListenerAutoConfiguration {

    @Bean
    ChatModelListener testChatModelListener() {
        return new ChatModelListener() {};
    }
}
