package dev.langchain4j.community.cohere;

import dev.langchain4j.community.model.CohereChatModel;
import dev.langchain4j.community.model.CohereStreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@EnableAutoConfiguration
@EnableConfigurationProperties({CohereChatModelProperties.class, CohereStreamingChatModelProperties.class})
public class CohereAutoConfiguration {

    @Bean
    @ConditionalOnProperty(CohereChatModelProperties.PREFIX + ".api-key")
    CohereChatModel cohereChatModel(
            CohereChatModelProperties properties, ObjectProvider<ChatModelListener> listenerProvider) {
        return CohereChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .temperature(properties.getTemperature())
                .topP(properties.getTopP())
                .stopSequences(properties.getStopSequences())
                .maxTokens(properties.getMaxTokens())
                .presencePenalty(properties.getPresencePenalty())
                .frequencyPenalty(properties.getFrequencePenalty())
                .timeout(toDuration(properties.getTimeout()))
                .maxRetries(properties.getMaxRetries())
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
    CohereStreamingChatModel cohereStreamingChatModel(
            CohereStreamingChatModelProperties properties, ObjectProvider<ChatModelListener> listenerProvider) {
        return CohereStreamingChatModel.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .temperature(properties.getTemperature())
                .topP(properties.getTopP())
                .stopSequences(properties.getStopSequences())
                .maxTokens(properties.getMaxTokens())
                .presencePenalty(properties.getPresencePenalty())
                .frequencyPenalty(properties.getFrequencePenalty())
                .timeout(toDuration(properties.getTimeout()))
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

    private static Duration toDuration(Long milliseconds) {
        if (milliseconds == null) {
            return null;
        }

        return Duration.ofMillis(milliseconds);
    }
}
