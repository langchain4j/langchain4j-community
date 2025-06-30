package dev.langchain4j.community.qianfan.spring;

import static dev.langchain4j.community.qianfan.spring.QianfanProperties.PREFIX;

import dev.langchain4j.community.model.qianfan.QianfanChatModel;
import dev.langchain4j.community.model.qianfan.QianfanEmbeddingModel;
import dev.langchain4j.community.model.qianfan.QianfanLanguageModel;
import dev.langchain4j.community.model.qianfan.QianfanStreamingChatModel;
import dev.langchain4j.community.model.qianfan.QianfanStreamingLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(QianfanProperties.class)
public class QianfanAutoConfiguration {

    @Bean
    @ConditionalOnProperty(PREFIX + ".chat-model.api-key")
    QianfanChatModel qianfanChatModel(
            QianfanProperties properties, ObjectProvider<ChatModelListener> listenerProvider) {
        QianfanChatModelProperties chatModelProperties = properties.getChatModel();
        return QianfanChatModel.builder()
                .baseUrl(chatModelProperties.getBaseUrl())
                .apiKey(chatModelProperties.getApiKey())
                .secretKey(chatModelProperties.getSecretKey())
                .endpoint(chatModelProperties.getEndpoint())
                .penaltyScore(chatModelProperties.getPenaltyScore())
                .modelName(chatModelProperties.getModelName())
                .temperature(chatModelProperties.getTemperature())
                .topP(chatModelProperties.getTopP())
                .responseFormat(chatModelProperties.getResponseFormat())
                .maxRetries(chatModelProperties.getMaxRetries())
                .logRequests(chatModelProperties.getLogRequests())
                .logResponses(chatModelProperties.getLogResponses())
                .userId(chatModelProperties.getUserId())
                .maxOutputTokens(chatModelProperties.getMaxOutputTokens())
                .stop(chatModelProperties.getStop())
                .listeners(listenerProvider.stream().toList())
                .build();
    }

    @Bean
    @ConditionalOnProperty(PREFIX + ".streaming-chat-model.api-key")
    QianfanStreamingChatModel qianfanStreamingChatModel(
            QianfanProperties properties, ObjectProvider<ChatModelListener> listenerProvider) {
        QianfanChatModelProperties chatModelProperties = properties.getStreamingChatModel();
        return QianfanStreamingChatModel.builder()
                .endpoint(chatModelProperties.getEndpoint())
                .penaltyScore(chatModelProperties.getPenaltyScore())
                .temperature(chatModelProperties.getTemperature())
                .topP(chatModelProperties.getTopP())
                .baseUrl(chatModelProperties.getBaseUrl())
                .apiKey(chatModelProperties.getApiKey())
                .secretKey(chatModelProperties.getSecretKey())
                .modelName(chatModelProperties.getModelName())
                .responseFormat(chatModelProperties.getResponseFormat())
                .logRequests(chatModelProperties.getLogRequests())
                .logResponses(chatModelProperties.getLogResponses())
                .userId(chatModelProperties.getUserId())
                .maxOutputTokens(chatModelProperties.getMaxOutputTokens())
                .stop(chatModelProperties.getStop())
                .listeners(listenerProvider.stream().toList())
                .build();
    }

    @Bean
    @ConditionalOnProperty(PREFIX + ".language-model.api-key")
    QianfanLanguageModel qianfanLanguageModel(QianfanProperties properties) {
        QianfanLanguageModelProperties languageModelProperties = properties.getLanguageModel();
        return QianfanLanguageModel.builder()
                .endpoint(languageModelProperties.getEndpoint())
                .penaltyScore(languageModelProperties.getPenaltyScore())
                .topK(languageModelProperties.getTopK())
                .topP(languageModelProperties.getTopP())
                .baseUrl(languageModelProperties.getBaseUrl())
                .apiKey(languageModelProperties.getApiKey())
                .secretKey(languageModelProperties.getSecretKey())
                .modelName(languageModelProperties.getModelName())
                .temperature(languageModelProperties.getTemperature())
                .maxRetries(languageModelProperties.getMaxRetries())
                .logRequests(languageModelProperties.getLogRequests())
                .logResponses(languageModelProperties.getLogResponses())
                .build();
    }

    @Bean
    @ConditionalOnProperty(PREFIX + ".streaming-language-model.api-key")
    QianfanStreamingLanguageModel openAiStreamingLanguageModel(QianfanProperties properties) {
        QianfanLanguageModelProperties languageModelProperties = properties.getStreamingLanguageModel();
        return QianfanStreamingLanguageModel.builder()
                .endpoint(languageModelProperties.getEndpoint())
                .penaltyScore(languageModelProperties.getPenaltyScore())
                .topK(languageModelProperties.getTopK())
                .topP(languageModelProperties.getTopP())
                .baseUrl(languageModelProperties.getBaseUrl())
                .apiKey(languageModelProperties.getApiKey())
                .secretKey(languageModelProperties.getSecretKey())
                .modelName(languageModelProperties.getModelName())
                .temperature(languageModelProperties.getTemperature())
                .logRequests(languageModelProperties.getLogRequests())
                .logResponses(languageModelProperties.getLogResponses())
                .build();
    }

    @Bean
    @ConditionalOnProperty(PREFIX + ".embedding-model.api-key")
    QianfanEmbeddingModel qianfanEmbeddingModel(QianfanProperties properties) {
        QianfanEmbeddingModelProperties qianfanEmbeddingModelProperties = properties.getEmbeddingModel();
        return QianfanEmbeddingModel.builder()
                .baseUrl(qianfanEmbeddingModelProperties.getBaseUrl())
                .endpoint(qianfanEmbeddingModelProperties.getEndpoint())
                .apiKey(qianfanEmbeddingModelProperties.getApiKey())
                .secretKey(qianfanEmbeddingModelProperties.getSecretKey())
                .modelName(qianfanEmbeddingModelProperties.getModelName())
                .user(qianfanEmbeddingModelProperties.getUser())
                .maxRetries(qianfanEmbeddingModelProperties.getMaxRetries())
                .logRequests(qianfanEmbeddingModelProperties.getLogRequests())
                .logResponses(qianfanEmbeddingModelProperties.getLogResponses())
                .build();
    }
}
