package dev.langchain4j.community.qianfan.spring;

import static dev.langchain4j.community.qianfan.spring.Properties.PREFIX;

import dev.langchain4j.community.model.qianfan.QianfanChatModel;
import dev.langchain4j.community.model.qianfan.QianfanEmbeddingModel;
import dev.langchain4j.community.model.qianfan.QianfanLanguageModel;
import dev.langchain4j.community.model.qianfan.QianfanStreamingChatModel;
import dev.langchain4j.community.model.qianfan.QianfanStreamingLanguageModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(Properties.class)
public class AutoConfig {

    @Bean
    @ConditionalOnProperty(PREFIX + ".chat-model.api-key")
    QianfanChatModel qianfanChatModel(Properties properties) {
        ChatModelProperties chatModelProperties = properties.getChatModel();
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
                .build();
    }

    @Bean
    @ConditionalOnProperty(PREFIX + ".streaming-chat-model.api-key")
    QianfanStreamingChatModel qianfanStreamingChatModel(Properties properties) {
        ChatModelProperties chatModelProperties = properties.getStreamingChatModel();
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
                .build();
    }

    @Bean
    @ConditionalOnProperty(PREFIX + ".language-model.api-key")
    QianfanLanguageModel qianfanLanguageModel(Properties properties) {
        LanguageModelProperties languageModelProperties = properties.getLanguageModel();
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
    QianfanStreamingLanguageModel openAiStreamingLanguageModel(Properties properties) {
        LanguageModelProperties languageModelProperties = properties.getStreamingLanguageModel();
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
    QianfanEmbeddingModel qianfanEmbeddingModel(Properties properties) {
        EmbeddingModelProperties embeddingModelProperties = properties.getEmbeddingModel();
        return QianfanEmbeddingModel.builder()
                .baseUrl(embeddingModelProperties.getBaseUrl())
                .endpoint(embeddingModelProperties.getEndpoint())
                .apiKey(embeddingModelProperties.getApiKey())
                .secretKey(embeddingModelProperties.getSecretKey())
                .modelName(embeddingModelProperties.getModelName())
                .user(embeddingModelProperties.getUser())
                .maxRetries(embeddingModelProperties.getMaxRetries())
                .logRequests(embeddingModelProperties.getLogRequests())
                .logResponses(embeddingModelProperties.getLogResponses())
                .build();
    }
}
