package dev.langchain4j.community.dashscope.spring;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class TestChatModelListenerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TestChatModelListenerAutoConfiguration.class);

    @Bean
    public ChatModelListener testChatModelListener() {
        return new ChatModelListener() {
            @Override
            public void onRequest(final ChatModelRequestContext requestContext) {
                log.info("onRequest() {}", requestContext);
            }

            @Override
            public void onResponse(final ChatModelResponseContext responseContext) {
                log.info("onResponse() {}", responseContext);
            }

            @Override
            public void onError(final ChatModelErrorContext errorContext) {
                log.info("onError() {}", errorContext);
            }
        };
    }
}
