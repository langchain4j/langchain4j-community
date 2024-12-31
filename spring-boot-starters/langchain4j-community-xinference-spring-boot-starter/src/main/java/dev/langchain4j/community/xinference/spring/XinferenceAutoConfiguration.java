package dev.langchain4j.community.xinference.spring;

import dev.langchain4j.community.model.xinference.XinferenceChatModel;
import dev.langchain4j.community.model.xinference.XinferenceEmbeddingModel;
import dev.langchain4j.community.model.xinference.XinferenceImageModel;
import dev.langchain4j.community.model.xinference.XinferenceLanguageModel;
import dev.langchain4j.community.model.xinference.XinferenceScoringModel;
import dev.langchain4j.community.model.xinference.XinferenceStreamingChatModel;
import dev.langchain4j.community.model.xinference.XinferenceStreamingLanguageModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties({
    ChatModelProperties.class,
    StreamingChatModelProperties.class,
    LanguageModelProperties.class,
    StreamingLanguageModelProperties.class,
    EmbeddingModelProperties.class,
    ImageModelProperties.class,
    ScoringModelProperties.class
})
public class XinferenceAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(ChatModelProperties.PREFIX + ".base-url")
    public XinferenceChatModel xinferenceChatModel(ChatModelProperties chatModelProperties) {
        return XinferenceChatModel.builder()
                .baseUrl(chatModelProperties.getBaseUrl())
                .apiKey(chatModelProperties.getApiKey())
                .modelName(chatModelProperties.getModelName())
                .temperature(chatModelProperties.getTemperature())
                .topP(chatModelProperties.getTopP())
                .stop(chatModelProperties.getStop())
                .maxTokens(chatModelProperties.getMaxTokens())
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
    @ConditionalOnMissingBean
    @ConditionalOnProperty(StreamingChatModelProperties.PREFIX + ".base-url")
    public XinferenceStreamingChatModel xinferenceStreamingChatModel(
            StreamingChatModelProperties streamingChatModelProperties) {
        return XinferenceStreamingChatModel.builder()
                .baseUrl(streamingChatModelProperties.getBaseUrl())
                .apiKey(streamingChatModelProperties.getApiKey())
                .modelName(streamingChatModelProperties.getModelName())
                .temperature(streamingChatModelProperties.getTemperature())
                .topP(streamingChatModelProperties.getTopP())
                .stop(streamingChatModelProperties.getStop())
                .maxTokens(streamingChatModelProperties.getMaxTokens())
                .presencePenalty(streamingChatModelProperties.getPresencePenalty())
                .frequencyPenalty(streamingChatModelProperties.getFrequencyPenalty())
                .seed(streamingChatModelProperties.getSeed())
                .user(streamingChatModelProperties.getUser())
                .toolChoice(streamingChatModelProperties.getToolChoice())
                .parallelToolCalls(streamingChatModelProperties.getParallelToolCalls())
                .timeout(streamingChatModelProperties.getTimeout())
                .proxy(ProxyProperties.convert(streamingChatModelProperties.getProxy()))
                .logRequests(streamingChatModelProperties.getLogRequests())
                .logResponses(streamingChatModelProperties.getLogResponses())
                .customHeaders(streamingChatModelProperties.getCustomHeaders())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(LanguageModelProperties.PREFIX + ".base-url")
    public XinferenceLanguageModel xinferenceLanguageModel(LanguageModelProperties languageModelProperties) {
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
    @ConditionalOnMissingBean
    @ConditionalOnProperty(StreamingLanguageModelProperties.PREFIX + ".base-url")
    public XinferenceStreamingLanguageModel xinferenceStreamingLanguageModel(
            StreamingLanguageModelProperties streamingLanguageModelProperties) {
        return XinferenceStreamingLanguageModel.builder()
                .baseUrl(streamingLanguageModelProperties.getBaseUrl())
                .apiKey(streamingLanguageModelProperties.getApiKey())
                .modelName(streamingLanguageModelProperties.getModelName())
                .maxTokens(streamingLanguageModelProperties.getMaxTokens())
                .temperature(streamingLanguageModelProperties.getTemperature())
                .topP(streamingLanguageModelProperties.getTopP())
                .logprobs(streamingLanguageModelProperties.getLogprobs())
                .echo(streamingLanguageModelProperties.getEcho())
                .stop(streamingLanguageModelProperties.getStop())
                .presencePenalty(streamingLanguageModelProperties.getPresencePenalty())
                .frequencyPenalty(streamingLanguageModelProperties.getFrequencyPenalty())
                .user(streamingLanguageModelProperties.getUser())
                .timeout(streamingLanguageModelProperties.getTimeout())
                .proxy(ProxyProperties.convert(streamingLanguageModelProperties.getProxy()))
                .logRequests(streamingLanguageModelProperties.getLogRequests())
                .logResponses(streamingLanguageModelProperties.getLogResponses())
                .customHeaders(streamingLanguageModelProperties.getCustomHeaders())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(EmbeddingModelProperties.PREFIX + ".base-url")
    public XinferenceEmbeddingModel xinferenceEmbeddingModel(EmbeddingModelProperties embeddingModelProperties) {
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
    @ConditionalOnMissingBean
    @ConditionalOnProperty(ImageModelProperties.PREFIX + ".base-url")
    public XinferenceImageModel xinferenceImageModel(ImageModelProperties imageModelProperties) {
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
    @ConditionalOnMissingBean
    @ConditionalOnProperty(ScoringModelProperties.PREFIX + ".base-url")
    public XinferenceScoringModel xinferenceScoringModel(ScoringModelProperties scoringModelProperties) {
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
