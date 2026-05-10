package dev.langchain4j.community.cohere.spring;

import dev.langchain4j.community.model.CohereChatModel;
import dev.langchain4j.community.model.CohereStreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties({CohereChatModelProperties.class, CohereStreamingChatModelProperties.class})
public class CohereAutoConfiguration {

    @Bean
    @ConditionalOnProperty(CohereChatModelProperties.PREFIX + ".api-key")
    public CohereChatModel cohereChatModel(
            CohereChatModelProperties properties, ObjectProvider<ChatModelListener> listenerProvider) {
        return CohereChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .timeout(toDuration(properties.getTimeout()))
                .maxRetries(properties.getMaxRetries())
                .modelName(properties.getModelName())
                .temperature(properties.getTemperature())
                .topP(properties.getTopP())
                .topK(properties.getTopK())
                .frequencyPenalty(properties.getFrequencyPenalty())
                .presencePenalty(properties.getPresencePenalty())
                .maxTokens(properties.getMaxTokens())
                .stopSequences(properties.getStopSequences())
                .thinkingType(properties.getThinkingType())
                .thinkingTokenBudget(properties.getThinkingTokenBudget())
                .safetyMode(properties.getSafetyMode())
                .priority(properties.getPriority())
                .seed(properties.getSeed())
                .logprobs(properties.getLogprobs())
                .strictTools(properties.getStrictTools())
                .logRequests(properties.getLogRequests())
                .logResponses(properties.getLogResponses())
                .listeners(listenerProvider.stream().toList())
                .build();
    }

    @Bean
    @ConditionalOnProperty(CohereStreamingChatModelProperties.PREFIX + ".api-key")
    public CohereStreamingChatModel cohereStreamingChatModel(
            CohereStreamingChatModelProperties properties, ObjectProvider<ChatModelListener> listenerProvider) {
        return CohereStreamingChatModel.builder()
                .apiKey(properties.getApiKey())
                .baseUrl(properties.getBaseUrl())
                .timeout(toDuration(properties.getTimeout()))
                .modelName(properties.getModelName())
                .temperature(properties.getTemperature())
                .topP(properties.getTopP())
                .topK(properties.getTopK())
                .presencePenalty(properties.getPresencePenalty())
                .frequencyPenalty(properties.getFrequencyPenalty())
                .maxTokens(properties.getMaxTokens())
                .stopSequences(properties.getStopSequences())
                .thinkingType(properties.getThinkingType())
                .thinkingTokenBudget(properties.getThinkingTokenBudget())
                .safetyMode(properties.getSafetyMode())
                .priority(properties.getPriority())
                .seed(properties.getSeed())
                .logprobs(properties.getLogProbs())
                .strictTools(properties.getStrictTools())
                .logRequests(properties.getLogRequests())
                .logResponses(properties.getLogResponses())
                .listeners(listenerProvider.stream().toList())
                .build();
    }

    private static Duration toDuration(Long milliseconds) {
        if (milliseconds == null) {
            return null;
        }
        return Duration.ofMillis(milliseconds);
    }
}
