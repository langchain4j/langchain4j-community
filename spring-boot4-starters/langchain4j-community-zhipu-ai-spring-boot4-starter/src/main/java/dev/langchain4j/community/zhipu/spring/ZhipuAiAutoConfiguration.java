package dev.langchain4j.community.zhipu.spring;

import dev.langchain4j.community.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.community.model.zhipu.ZhipuAiEmbeddingModel;
import dev.langchain4j.community.model.zhipu.ZhipuAiImageModel;
import dev.langchain4j.community.model.zhipu.ZhipuAiStreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(ZhipuAiProperties.class)
public class ZhipuAiAutoConfiguration {

    @Bean
    @ConditionalOnProperty(ZhipuAiProperties.PREFIX + ".chat-model.api-key")
    ZhipuAiChatModel zhipuAiChatModel(
            ZhipuAiProperties properties, ObjectProvider<ChatModelListener> listenerProvider) {
        ZhipuAiChatModelProperties chatModelProperties = properties.getChatModel();
        return ZhipuAiChatModel.builder()
                .baseUrl(chatModelProperties.getBaseUrl())
                .apiKey(chatModelProperties.getApiKey())
                .model(chatModelProperties.getModel())
                .temperature(chatModelProperties.getTemperature())
                .topP(chatModelProperties.getTopP())
                .stops(chatModelProperties.getStops())
                .responseFormat(chatModelProperties.getResponseFormat())
                .maxRetries(chatModelProperties.getMaxRetries())
                .maxToken(chatModelProperties.getMaxToken())
                .logRequests(chatModelProperties.getLogRequests())
                .logResponses(chatModelProperties.getLogResponses())
                .doSample(chatModelProperties.getDoSample())
                .thinking(chatModelProperties.getThinking())
                .listeners(listenerProvider.stream().toList())
                .callTimeout(toDuration(chatModelProperties.getCallTimeout()))
                .connectTimeout(toDuration(chatModelProperties.getConnectTimeout()))
                .readTimeout(toDuration(chatModelProperties.getReadTimeout()))
                .writeTimeout(toDuration(chatModelProperties.getWriteTimeout()))
                .build();
    }

    @Bean
    @ConditionalOnProperty(ZhipuAiProperties.PREFIX + ".streaming-chat-model.api-key")
    ZhipuAiStreamingChatModel zhipuAiStreamingChatModel(
            ZhipuAiProperties properties, ObjectProvider<ChatModelListener> listenerProvider) {
        ZhipuAiStreamingChatModelProperties streamingChatModelProperties = properties.getStreamingChatModel();
        return ZhipuAiStreamingChatModel.builder()
                .baseUrl(streamingChatModelProperties.getBaseUrl())
                .apiKey(streamingChatModelProperties.getApiKey())
                .model(streamingChatModelProperties.getModel())
                .temperature(streamingChatModelProperties.getTemperature())
                .topP(streamingChatModelProperties.getTopP())
                .stops(streamingChatModelProperties.getStops())
                .responseFormat(streamingChatModelProperties.getResponseFormat())
                .maxToken(streamingChatModelProperties.getMaxToken())
                .logRequests(streamingChatModelProperties.getLogRequests())
                .logResponses(streamingChatModelProperties.getLogResponses())
                .doSample(streamingChatModelProperties.getDoSample())
                .toolStream(streamingChatModelProperties.getToolStream())
                .thinking(streamingChatModelProperties.getThinking())
                .listeners(listenerProvider.stream().toList())
                .callTimeout(toDuration(streamingChatModelProperties.getCallTimeout()))
                .connectTimeout(toDuration(streamingChatModelProperties.getConnectTimeout()))
                .readTimeout(toDuration(streamingChatModelProperties.getReadTimeout()))
                .writeTimeout(toDuration(streamingChatModelProperties.getWriteTimeout()))
                .build();
    }

    @Bean
    @ConditionalOnProperty(ZhipuAiProperties.PREFIX + ".embedding-model.api-key")
    ZhipuAiEmbeddingModel zhipuAiEmbeddingModel(ZhipuAiProperties properties) {
        ZhipuAiEmbeddingModelProperties embeddingModelProperties = properties.getEmbeddingModel();
        return ZhipuAiEmbeddingModel.builder()
                .baseUrl(embeddingModelProperties.getBaseUrl())
                .apiKey(embeddingModelProperties.getApiKey())
                .model(embeddingModelProperties.getModel())
                .dimensions(embeddingModelProperties.getDimensions())
                .maxRetries(embeddingModelProperties.getMaxRetries())
                .logRequests(embeddingModelProperties.getLogRequests())
                .logResponses(embeddingModelProperties.getLogResponses())
                .callTimeout(toDuration(embeddingModelProperties.getCallTimeout()))
                .connectTimeout(toDuration(embeddingModelProperties.getConnectTimeout()))
                .readTimeout(toDuration(embeddingModelProperties.getReadTimeout()))
                .writeTimeout(toDuration(embeddingModelProperties.getWriteTimeout()))
                .build();
    }

    @Bean
    @ConditionalOnProperty(ZhipuAiProperties.PREFIX + ".image-model.api-key")
    ZhipuAiImageModel zhipuAiImageModel(ZhipuAiProperties properties) {
        ZhipuAiImageModelProperties imageModelProperties = properties.getImageModel();
        return ZhipuAiImageModel.builder()
                .model(imageModelProperties.getModel())
                .userId(imageModelProperties.getUserId())
                .apiKey(imageModelProperties.getApiKey())
                .baseUrl(imageModelProperties.getBaseUrl())
                .maxRetries(imageModelProperties.getMaxRetries())
                .logRequests(imageModelProperties.getLogRequests())
                .logResponses(imageModelProperties.getLogResponses())
                .callTimeout(toDuration(imageModelProperties.getCallTimeout()))
                .connectTimeout(toDuration(imageModelProperties.getConnectTimeout()))
                .readTimeout(toDuration(imageModelProperties.getReadTimeout()))
                .writeTimeout(toDuration(imageModelProperties.getWriteTimeout()))
                .build();
    }

    private static Duration toDuration(Long milliseconds) {
        if (milliseconds == null) {
            return null;
        }
        return Duration.ofMillis(milliseconds);
    }
}
