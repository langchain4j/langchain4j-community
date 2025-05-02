package dev.langchain4j.community.model.qianfan;

import static dev.langchain4j.community.model.qianfan.InternalQianfanHelper.finishReasonFrom;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.community.model.qianfan.client.Usage;
import dev.langchain4j.community.model.qianfan.client.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.qianfan.client.chat.FunctionCall;
import dev.langchain4j.community.model.qianfan.client.completion.CompletionResponse;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class needs to be thread safe because it is called when a streaming result comes back
 * and there is no guarantee that this thread will be the same as the one that initiated the request,
 * in fact it almost certainly won't be.
 */
public class QianfanStreamingResponseBuilder {

    private final StringBuffer contentBuilder = new StringBuffer();

    private final StringBuffer toolNameBuilder = new StringBuffer();
    private final StringBuffer toolArgumentsBuilder = new StringBuffer();

    private final Map<Integer, ToolExecutionRequestBuilder> indexToToolExecutionRequestBuilder =
            new ConcurrentHashMap<>();

    private volatile String finishReason;

    private Integer inputTokenCount;

    private Integer outputTokenCount;

    public QianfanStreamingResponseBuilder(Integer inputTokenCount) {
        this.inputTokenCount = inputTokenCount;
    }

    public void append(ChatCompletionResponse partialResponse) {

        if (partialResponse == null) {
            return;
        }

        String finishReason = partialResponse.getFinishReason();
        if (finishReason != null) {
            this.finishReason = finishReason;
        }

        String content = partialResponse.getResult();
        if (content != null) {
            contentBuilder.append(content);
        }

        Usage usage = partialResponse.getUsage();
        if (usage != null) {
            inputTokenCount = usage.getPromptTokens();
            outputTokenCount = usage.getCompletionTokens();
        }

        FunctionCall functionCall = partialResponse.getFunctionCall();

        if (functionCall != null) {
            if (functionCall.getName() != null) {
                toolNameBuilder.append(functionCall.getName());
            }

            if (functionCall.getArguments() != null) {
                toolArgumentsBuilder.append(functionCall.getArguments());
            }
        }
    }

    public void append(CompletionResponse partialResponse) {
        if (partialResponse == null) {
            return;
        }

        String result = partialResponse.getResult();
        if (Utils.isNullOrBlank(result)) {
            return;
        }

        String finishReason = partialResponse.getFinishReason();
        if (finishReason != null) {
            this.finishReason = finishReason;
        }

        String token = partialResponse.getResult();
        if (token != null) {
            contentBuilder.append(token);
        }
    }

    public ChatResponse build(TokenCountEstimator TokenCountEstimator, boolean forcefulToolExecution) {

        String content = contentBuilder.toString();
        if (!content.isEmpty()) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(content))
                    .tokenUsage(tokenUsage(content, TokenCountEstimator))
                    .finishReason(finishReasonFrom(finishReason))
                    .build();
        }

        String toolName = toolNameBuilder.toString();
        if (!toolName.isEmpty()) {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name(toolName)
                    .arguments(toolArgumentsBuilder.toString())
                    .build();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(toolExecutionRequest))
                    .tokenUsage(
                            tokenUsage(singletonList(toolExecutionRequest), TokenCountEstimator, forcefulToolExecution))
                    .finishReason(finishReasonFrom(finishReason))
                    .build();
        }

        if (!indexToToolExecutionRequestBuilder.isEmpty()) {
            List<ToolExecutionRequest> toolExecutionRequests = indexToToolExecutionRequestBuilder.values().stream()
                    .map(it -> ToolExecutionRequest.builder()
                            .id(it.idBuilder.toString())
                            .name(it.nameBuilder.toString())
                            .arguments(it.argumentsBuilder.toString())
                            .build())
                    .collect(toList());
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(toolExecutionRequests))
                    .tokenUsage(tokenUsage(toolExecutionRequests, TokenCountEstimator, forcefulToolExecution))
                    .finishReason(finishReasonFrom(finishReason))
                    .build();
        }

        return null;
    }

    public Response<String> build(TokenCountEstimator TokenCountEstimator) {

        String content = contentBuilder.toString();
        if (!content.isEmpty()) {
            return Response.from(content, tokenUsage(content, TokenCountEstimator), finishReasonFrom(finishReason));
        }
        return null;
    }

    private TokenUsage tokenUsage(String content, TokenCountEstimator TokenCountEstimator) {
        if (TokenCountEstimator == null) {
            return null;
        }
        int outputTokenCount = TokenCountEstimator.estimateTokenCountInText(content);
        return new TokenUsage(inputTokenCount, outputTokenCount);
    }

    private TokenUsage tokenUsage(
            List<ToolExecutionRequest> toolExecutionRequests,
            TokenCountEstimator TokenCountEstimator,
            boolean forcefulToolExecution) {
        if (TokenCountEstimator == null) {
            return null;
        }

        // Tool output token estimation has been removed since 1.0.0-beta1

        return new TokenUsage(inputTokenCount, 0);
    }

    private static class ToolExecutionRequestBuilder {

        private final StringBuffer idBuilder = new StringBuffer();
        private final StringBuffer nameBuilder = new StringBuffer();
        private final StringBuffer argumentsBuilder = new StringBuffer();
    }

    public ChatResponse build() {
        String content = contentBuilder.toString();
        if (!content.isEmpty()) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(content))
                    .tokenUsage(new TokenUsage(inputTokenCount, outputTokenCount))
                    .finishReason(finishReasonFrom(finishReason))
                    .build();
        }

        String toolName = toolNameBuilder.toString();
        if (!toolName.isEmpty()) {
            ToolExecutionRequest toolExecutionRequest = ToolExecutionRequest.builder()
                    .name(toolName)
                    .arguments(toolArgumentsBuilder.toString())
                    .build();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(toolExecutionRequest))
                    .tokenUsage(new TokenUsage(inputTokenCount, outputTokenCount))
                    .finishReason(finishReasonFrom(finishReason))
                    .build();
        }

        if (!indexToToolExecutionRequestBuilder.isEmpty()) {
            List<ToolExecutionRequest> toolExecutionRequests = indexToToolExecutionRequestBuilder.values().stream()
                    .map(it -> ToolExecutionRequest.builder()
                            .id(it.idBuilder.toString())
                            .name(it.nameBuilder.toString())
                            .arguments(it.argumentsBuilder.toString())
                            .build())
                    .collect(toList());
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(toolExecutionRequests))
                    .tokenUsage(new TokenUsage(inputTokenCount, outputTokenCount))
                    .finishReason(finishReasonFrom(finishReason))
                    .build();
        }

        return null;
    }
}
