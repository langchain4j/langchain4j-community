package dev.langchain4j.community.xinference.spring;

import static dev.langchain4j.community.xinference.spring.Properties.PREFIX;

import dev.langchain4j.community.model.xinference.XinferenceChatModel;
import dev.langchain4j.community.model.xinference.XinferenceEmbeddingModel;
import dev.langchain4j.community.model.xinference.XinferenceImageModel;
import dev.langchain4j.community.model.xinference.XinferenceLanguageModel;
import dev.langchain4j.community.model.xinference.XinferenceScoringModel;
import dev.langchain4j.community.model.xinference.XinferenceStreamingChatModel;
import dev.langchain4j.community.model.xinference.XinferenceStreamingLanguageModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(Properties.class)
public class XinferenceAutoConfiguration {
    @Bean
    @ConditionalOnProperty(PREFIX + ".chat-model.base-url")
    public XinferenceChatModel xinferenceChatModel(Properties properties) {
        ChatModelProperties chatModelProperties = properties.getChatModel();
        return XinferenceChatModel.builder()
                .baseUrl(chatModelProperties.getBaseUrl())
                .apiKey(chatModelProperties.getApiKey())
                .modelName(chatModelProperties.getModelName())
                .temperature(chatModelProperties.getTemperature())
                .topP(chatModelProperties.getTopP())
                .stop(chatModelProperties.getStop())
                .maxTokens(chatModelProperties.getMaxRetries())
                .presencePenalty(chatModelProperties.getPresencePenalty())
                .frequencyPenalty(chatModelProperties.getFrequencyPenalty())
                .seed(chatModelProperties.getSeed())
                .user(chatModelProperties.getUser())
                .toolChoice(chatModelProperties.getToolChoice())
                .parallelToolCalls(chatModelProperties.getParallelToolCalls())
                .maxRetries(chatModelProperties.getMaxRetries())
                .timeout(chatModelProperties.getTimeout())
                .proxy(ProxyProperties.convert(chatModelProperties.getProxy()))
                .logRequests(chatModelProperties.getLogRequests())
                .logResponses(chatModelProperties.getLogResponses())
                .customHeaders(chatModelProperties.getCustomHeaders())
                .build();
    }

    @Bean
    @ConditionalOnProperty(PREFIX + ".streaming-chat-model.base-url")
    public XinferenceStreamingChatModel xinferenceStreamingChatModel(Properties properties) {
        ChatModelProperties chatModelProperties = properties.getStreamingChatModel();
        return XinferenceStreamingChatModel.builder()
                .baseUrl(chatModelProperties.getBaseUrl())
                .apiKey(chatModelProperties.getApiKey())
                .modelName(chatModelProperties.getModelName())
                .temperature(chatModelProperties.getTemperature())
                .topP(chatModelProperties.getTopP())
                .stop(chatModelProperties.getStop())
                .maxTokens(chatModelProperties.getMaxRetries())
                .presencePenalty(chatModelProperties.getPresencePenalty())
                .frequencyPenalty(chatModelProperties.getFrequencyPenalty())
                .seed(chatModelProperties.getSeed())
                .user(chatModelProperties.getUser())
                .toolChoice(chatModelProperties.getToolChoice())
                .parallelToolCalls(chatModelProperties.getParallelToolCalls())
                .timeout(chatModelProperties.getTimeout())
                .proxy(ProxyProperties.convert(chatModelProperties.getProxy()))
                .logRequests(chatModelProperties.getLogRequests())
                .logResponses(chatModelProperties.getLogResponses())
                .customHeaders(chatModelProperties.getCustomHeaders())
                .build();
    }

    @Bean
    @ConditionalOnProperty(PREFIX + ".language-model.base-url")
    public XinferenceLanguageModel xinferenceLanguageModel(Properties properties) {
        LanguageModelProperties languageModelProperties = properties.getLanguageModel();
        return XinferenceLanguageModel.builder()
                .baseUrl(languageModelProperties.getBaseUrl())
                .apiKey(languageModelProperties.getApiKey())
                .modelName(languageModelProperties.getModelName())
                .maxTokens(languageModelProperties.getMaxTokens())
                .temperature(languageModelProperties.getTemperature())
                .topP(languageModelProperties.getTopP())
                .logprobs(languageModelProperties.getLogprobs())
                .echo(languageModelProperties.getEcho())
                .stop(languageModelProperties.getStop())
                .presencePenalty(languageModelProperties.getPresencePenalty())
                .frequencyPenalty(languageModelProperties.getFrequencyPenalty())
                .user(languageModelProperties.getUser())
                .maxRetries(languageModelProperties.getMaxRetries())
                .timeout(languageModelProperties.getTimeout())
                .proxy(ProxyProperties.convert(languageModelProperties.getProxy()))
                .logRequests(languageModelProperties.getLogRequests())
                .logResponses(languageModelProperties.getLogResponses())
                .customHeaders(languageModelProperties.getCustomHeaders())
                .build();
    }

    @Bean
    @ConditionalOnProperty(PREFIX + ".streaming-language-model.base-url")
    public XinferenceStreamingLanguageModel streamingLanguageModel(Properties properties) {
        LanguageModelProperties languageModelProperties = properties.getStreamingLanguageModel();
        return XinferenceStreamingLanguageModel.builder()
                .baseUrl(languageModelProperties.getBaseUrl())
                .apiKey(languageModelProperties.getApiKey())
                .modelName(languageModelProperties.getModelName())
                .maxTokens(languageModelProperties.getMaxTokens())
                .temperature(languageModelProperties.getTemperature())
                .topP(languageModelProperties.getTopP())
                .logprobs(languageModelProperties.getLogprobs())
                .echo(languageModelProperties.getEcho())
                .stop(languageModelProperties.getStop())
                .presencePenalty(languageModelProperties.getPresencePenalty())
                .frequencyPenalty(languageModelProperties.getFrequencyPenalty())
                .user(languageModelProperties.getUser())
                .timeout(languageModelProperties.getTimeout())
                .proxy(ProxyProperties.convert(languageModelProperties.getProxy()))
                .logRequests(languageModelProperties.getLogRequests())
                .logResponses(languageModelProperties.getLogResponses())
                .customHeaders(languageModelProperties.getCustomHeaders())
                .build();
    }

    @Bean
    @ConditionalOnProperty(PREFIX + ".embedding-model.base-url")
    public XinferenceEmbeddingModel embeddingModel(Properties properties) {
        EmbeddingModelProperties embeddingModelProperties = properties.getEmbeddingModel();
        return XinferenceEmbeddingModel.builder()
                .baseUrl(embeddingModelProperties.getBaseUrl())
                .apiKey(embeddingModelProperties.getApiKey())
                .modelName(embeddingModelProperties.getModelName())
                .user(embeddingModelProperties.getUser())
                .maxRetries(embeddingModelProperties.getMaxRetries())
                .timeout(embeddingModelProperties.getTimeout())
                .proxy(ProxyProperties.convert(embeddingModelProperties.getProxy()))
                .logRequests(embeddingModelProperties.getLogRequests())
                .logResponses(embeddingModelProperties.getLogResponses())
                .customHeaders(embeddingModelProperties.getCustomHeaders())
                .build();
    }

    @Bean
    @ConditionalOnProperty(PREFIX + ".image-model.base-url")
    public XinferenceImageModel imageModel(Properties properties) {
        ImageModelProperties imageModelProperties = properties.getImageModel();
        return XinferenceImageModel.builder()
                .baseUrl(imageModelProperties.getBaseUrl())
                .apiKey(imageModelProperties.getApiKey())
                .modelName(imageModelProperties.getModelName())
                .negativePrompt(imageModelProperties.getNegativePrompt())
                .responseFormat(imageModelProperties.getResponseFormat())
                .size(imageModelProperties.getSize())
                .kwargs(imageModelProperties.getKwargs())
                .user(imageModelProperties.getUser())
                .maxRetries(imageModelProperties.getMaxRetries())
                .timeout(imageModelProperties.getTimeout())
                .proxy(ProxyProperties.convert(imageModelProperties.getProxy()))
                .logRequests(imageModelProperties.getLogRequests())
                .logResponses(imageModelProperties.getLogResponses())
                .customHeaders(imageModelProperties.getCustomHeaders())
                .build();
    }

    @Bean
    @ConditionalOnProperty(PREFIX + ".scoring-model.base-url")
    public XinferenceScoringModel scoringModel(Properties properties) {
        ScoringModelProperties scoringModelProperties = properties.getScoringModel();
        return XinferenceScoringModel.builder()
                .baseUrl(scoringModelProperties.getBaseUrl())
                .apiKey(scoringModelProperties.getApiKey())
                .modelName(scoringModelProperties.getModelName())
                .topN(scoringModelProperties.getTopN())
                .returnDocuments(scoringModelProperties.getReturnDocuments())
                .returnLen(scoringModelProperties.getReturnLen())
                .maxRetries(scoringModelProperties.getMaxRetries())
                .timeout(scoringModelProperties.getTimeout())
                .proxy(ProxyProperties.convert(scoringModelProperties.getProxy()))
                .logRequests(scoringModelProperties.getLogRequests())
                .logResponses(scoringModelProperties.getLogResponses())
                .customHeaders(scoringModelProperties.getCustomHeaders())
                .build();
    }
}
