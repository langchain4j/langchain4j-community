package dev.langchain4j.community.novitaai.spring;

import static dev.langchain4j.community.novitaai.spring.Properties.PREFIX;

import dev.langchain4j.community.model.novitaai.NovitaAiChatModel;
import dev.langchain4j.community.model.novitaai.NovitaAiStreamingChatModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(Properties.class)
public class AutoConfig {

    @Bean
    @ConditionalOnProperty(PREFIX + ".chat-model.api-key")
    NovitaAiChatModel NovitaAiChatModel(Properties properties) {
        ChatModelProperties chatModelProperties = properties.getChatModel();
        return NovitaAiChatModel.builder()
                .apiKey(chatModelProperties.getApiKey())
                .modelName(chatModelProperties.getModelName())
                .build();
    }

    @Bean
    @ConditionalOnProperty(PREFIX + ".streaming-chat-model.api-key")
    NovitaAiStreamingChatModel NovitaAiStreamingChatModel(Properties properties) {
        ChatModelProperties chatModelProperties = properties.getStreamingChatModel();
        return NovitaAiStreamingChatModel.builder()
                .apiKey(chatModelProperties.getApiKey())
                .modelName(chatModelProperties.getModelName())
                .logResponses(chatModelProperties.getLogResponses())
                .build();
    }
}
