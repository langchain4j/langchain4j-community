package dev.langchain4j.community.model.util;

import dev.langchain4j.community.model.client.chat.CohereChatRequest;
import dev.langchain4j.community.model.client.chat.content.CohereContent;
import dev.langchain4j.community.model.client.chat.response.CohereChatResponse;
import dev.langchain4j.community.model.client.chat.response.CohereChatResponseMetadata;
import dev.langchain4j.community.model.client.chat.thinking.CohereThinking;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;
import java.util.Optional;

import static dev.langchain4j.community.model.client.chat.content.CohereContentType.TEXT;
import static dev.langchain4j.community.model.client.chat.content.CohereContentType.THINKING;
import static dev.langchain4j.community.model.util.CohereMapper.fromFinishReason;
import static dev.langchain4j.community.model.util.CohereMapper.toCohereChatMessages;
import static dev.langchain4j.community.model.util.CohereMapper.toCohereResponseFormat;
import static dev.langchain4j.community.model.util.CohereMapper.toCohereTools;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.joining;

public class CohereInternalHelper {

    private CohereInternalHelper() {}

    public static CohereChatRequest toCohereChatRequest(List<ChatMessage> messages,
                                                        CohereChatRequestParameters parameters) {
        CohereChatRequest.Builder builder = CohereChatRequest.builder()
                .model(parameters.modelName())
                .messages(toCohereChatMessages(messages))
                .responseFormat(toCohereResponseFormat(parameters.responseFormat()))
                .tools(toCohereTools(parameters.toolSpecifications()))
                .toolChoice(parameters.toolChoice())
                .temperature(parameters.temperature())
                .p(parameters.topP())
                .k(parameters.topK())
                .presencePenalty(parameters.presencePenalty())
                .frequencyPenalty(parameters.frequencyPenalty())
                .maxTokens(parameters.maxOutputTokens())
                .stopSequences(parameters.stopSequences().isEmpty() ? null : parameters.stopSequences())
                .stream(parameters.stream())
                .safetyMode(parameters.safetyMode())
                .priority(parameters.priority())
                .seed(parameters.seed())
                .logprobs(parameters.logprobs());

        if (parameters.thinkingType() != null
                || parameters.thinkingTokenBudget() != null) {
            builder.thinking(CohereThinking.builder()
                    .type(parameters.thinkingType())
                    .tokenBudget(parameters.thinkingTokenBudget())
                    .build());
        }

        return builder.build();
    }

    public static ChatResponse fromCohereChatResponse(CohereChatResponse response, String modelName) {
        String text = Optional.ofNullable(response.getMessage().getContent())
                .orElse(emptyList())
                .stream()
                .filter(content -> content.getType() == TEXT)
                .map(CohereContent::getText)
                .collect(collectingAndThen(joining("\n"), s -> s.isEmpty() ? null : s));

        String thinking = Optional.ofNullable(response.getMessage().getContent())
                .orElse(emptyList())
                .stream()
                .filter(content -> content.getType() == THINKING)
                .map(CohereContent::getThinking)
                .collect(collectingAndThen(joining("\n"), s -> s.isEmpty() ? null : s));

        CohereChatResponseMetadata.Builder metadataBuilder = CohereChatResponseMetadata.builder()
                .modelName(modelName)
                .id(response.getId())
                .tokenUsage(new TokenUsage(
                        response.getUsage().getTokens().getInputTokens().intValue(),
                        response.getUsage().getTokens().getOutputTokens().intValue()))
                .finishReason(fromFinishReason(response.getFinishReason()));

        if (!isNullOrEmpty(response.getLogprobs())) {
            metadataBuilder.logprobs(response.getLogprobs());
        }

        return ChatResponse.builder()
                .aiMessage(AiMessage.builder()
                        .text(text)
                        .thinking(thinking)
                        .toolExecutionRequests(response.getMessage().getToolCalls()
                                .stream()
                                .map(CohereMapper::toToolExecutionRequest)
                                .toList())
                        .build())
                .metadata(metadataBuilder.build())
                .build();
    }
}
