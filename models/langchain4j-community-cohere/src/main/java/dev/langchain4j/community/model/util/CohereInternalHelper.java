package dev.langchain4j.community.model.util;

import static dev.langchain4j.community.model.util.CohereMapper.toAiMessage;
import static dev.langchain4j.community.model.util.CohereMapper.toCohereChatMessages;
import static dev.langchain4j.community.model.util.CohereMapper.toCohereResponseFormat;
import static dev.langchain4j.community.model.util.CohereMapper.toCohereTools;
import static dev.langchain4j.community.model.util.CohereMapper.toFinishReason;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;

import dev.langchain4j.Internal;
import dev.langchain4j.community.model.client.CohereChatRequestParameters;
import dev.langchain4j.community.model.client.chat.CohereChatRequest;
import dev.langchain4j.community.model.client.chat.response.CohereChatResponse;
import dev.langchain4j.community.model.client.chat.response.CohereChatResponseMetadata;
import dev.langchain4j.community.model.client.chat.thinking.CohereThinking;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;

@Internal
public class CohereInternalHelper {

    private CohereInternalHelper() {}

    public static CohereChatRequest toCohereChatRequest(
            List<ChatMessage> messages, CohereChatRequestParameters parameters) {
        return toCohereChatRequest(messages, parameters, null);
    }

    public static CohereChatRequest toCohereStreamingChatRequest(
            List<ChatMessage> messages, CohereChatRequestParameters parameters) {
        return toCohereChatRequest(messages, parameters, true);
    }

    private static CohereChatRequest toCohereChatRequest(
            List<ChatMessage> messages, CohereChatRequestParameters parameters, Boolean stream) {
        CohereChatRequest.Builder builder = CohereChatRequest.builder()
                .model(parameters.modelName())
                .messages(isNullOrEmpty(messages) ? null : toCohereChatMessages(messages))
                .responseFormat(toCohereResponseFormat(parameters.responseFormat()))
                .tools(
                        isNullOrEmpty(parameters.toolSpecifications())
                                ? null
                                : toCohereTools(parameters.toolSpecifications()))
                .toolChoice(parameters.toolChoice())
                .temperature(parameters.temperature())
                .p(parameters.topP())
                .k(parameters.topK())
                .presencePenalty(parameters.presencePenalty())
                .frequencyPenalty(parameters.frequencyPenalty())
                .maxTokens(parameters.maxOutputTokens())
                .stopSequences(isNullOrEmpty(parameters.stopSequences()) ? null : parameters.stopSequences())
                .stream(stream)
                .safetyMode(parameters.safetyMode())
                .priority(parameters.priority())
                .seed(parameters.seed())
                .logprobs(parameters.logprobs())
                .strictTools(parameters.strictTools());

        if (parameters.thinkingType() != null || parameters.thinkingTokenBudget() != null) {
            builder.thinking(CohereThinking.builder()
                    .type(parameters.thinkingType())
                    .tokenBudget(parameters.thinkingTokenBudget())
                    .build());
        }

        return builder.build();
    }

    public static ChatResponse fromCohereChatResponse(CohereChatResponse response, String modelName) {
        AiMessage aiMessage = toAiMessage(response.getMessage());

        CohereChatResponseMetadata metadata = CohereChatResponseMetadata.builder()
                .modelName(modelName)
                .id(response.getId())
                .billedUnits(response.getUsage().getBilledUnits())
                .cachedTokens(response.getUsage().getCachedTokens())
                .tokenUsage(new TokenUsage(
                        response.getUsage().getTokens().getInputTokens(),
                        response.getUsage().getTokens().getOutputTokens()))
                .finishReason(toFinishReason(response.getFinishReason()))
                .logprobs(isNullOrEmpty(response.getLogprobs()) ? null : response.getLogprobs())
                .build();

        return ChatResponse.builder().aiMessage(aiMessage).metadata(metadata).build();
    }
}
