package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenHelper.answerFrom;
import static dev.langchain4j.community.model.dashscope.QwenHelper.finishReasonFrom;
import static dev.langchain4j.community.model.dashscope.QwenHelper.hasAnswer;
import static dev.langchain4j.community.model.dashscope.QwenHelper.isFunctionToolCalls;
import static dev.langchain4j.community.model.dashscope.QwenHelper.toolCallsFrom;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.util.stream.Collectors.toList;

import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.GenerationUsage;
import com.alibaba.dashscope.aigc.generation.SearchInfo;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationUsage;
import com.alibaba.dashscope.tools.ToolCallBase;
import com.alibaba.dashscope.tools.ToolCallFunction;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class QwenStreamingResponseBuilder {
    private final StringBuilder generatedContent = new StringBuilder();
    private final Map<Integer, ToolExecutionRequestBuilder> indexToToolExecutionRequestBuilder =
            new ConcurrentHashMap<>();
    private final String modelName;
    private String id;
    private Integer inputTokenCount;
    private Integer outputTokenCount;
    private FinishReason finishReason;
    private SearchInfo searchInfo;
    private boolean incrementalOutput;

    public QwenStreamingResponseBuilder(String modelName, boolean incrementalOutput) {
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.incrementalOutput = incrementalOutput;
    }

    public String append(GenerationResult partialResponse) {
        if (partialResponse == null) {
            return null;
        }

        if (!isNullOrBlank(partialResponse.getRequestId())) {
            id = partialResponse.getRequestId();
        }

        if (partialResponse.getOutput().getSearchInfo() != null) {
            searchInfo = partialResponse.getOutput().getSearchInfo();
        }

        GenerationUsage usage = partialResponse.getUsage();
        if (usage != null) {
            inputTokenCount = usage.getInputTokens();
            outputTokenCount = usage.getOutputTokens();
        }

        FinishReason latestFinishReason = finishReasonFrom(partialResponse);
        if (latestFinishReason != null) {
            finishReason = latestFinishReason;
        }

        if (hasAnswer(partialResponse)) {
            String partialContent = answerFrom(partialResponse);
            if (!incrementalOutput) {
                partialContent = partialContent.substring(generatedContent.length());
            }
            generatedContent.append(partialContent);
            return partialContent;
        } else if (isFunctionToolCalls(partialResponse)) {
            List<ToolCallBase> toolCalls = toolCallsFrom(partialResponse);
            for (int index = 0; index < toolCalls.size(); index++) {
                // It looks like the index of the list matches the 'index' property in the response,
                // which can't be directly accessed by java sdk.
                if (toolCalls.get(index) instanceof ToolCallFunction toolCall) {
                    ToolExecutionRequestBuilder toolExecutionRequestBuilder =
                            indexToToolExecutionRequestBuilder.computeIfAbsent(
                                    index, idx -> new ToolExecutionRequestBuilder());
                    if (toolCall.getId() != null) {
                        toolExecutionRequestBuilder.idBuilder.append(toolCall.getId());
                    }

                    ToolCallFunction.CallFunction functionCall = toolCall.getFunction();

                    if (functionCall.getName() != null) {
                        toolExecutionRequestBuilder.nameBuilder.append(functionCall.getName());
                    }

                    if (functionCall.getArguments() != null) {
                        toolExecutionRequestBuilder.argumentsBuilder.append(functionCall.getArguments());
                    }
                }
            }
        }

        return null;
    }

    public String append(MultiModalConversationResult partialResponse) {
        if (partialResponse == null) {
            return null;
        }

        if (!isNullOrBlank(partialResponse.getRequestId())) {
            id = partialResponse.getRequestId();
        }

        MultiModalConversationUsage usage = partialResponse.getUsage();
        if (usage != null) {
            inputTokenCount = usage.getInputTokens();
            outputTokenCount = usage.getOutputTokens();
        }

        FinishReason latestFinishReason = finishReasonFrom(partialResponse);
        if (latestFinishReason != null) {
            finishReason = latestFinishReason;
        }

        if (hasAnswer(partialResponse)) {
            String partialContent = answerFrom(partialResponse);
            if (!incrementalOutput) {
                partialContent = partialContent.substring(generatedContent.length());
            }
            generatedContent.append(partialContent);
            return partialContent;
        }

        return null;
    }

    public ChatResponse build() {
        String text = generatedContent.toString();

        if (!indexToToolExecutionRequestBuilder.isEmpty()) {
            List<ToolExecutionRequest> toolExecutionRequests = indexToToolExecutionRequestBuilder.values().stream()
                    .map(it -> ToolExecutionRequest.builder()
                            .id(it.idBuilder.toString())
                            .name(it.nameBuilder.toString())
                            .arguments(it.argumentsBuilder.toString())
                            .build())
                    .collect(toList());

            AiMessage aiMessage = isNullOrBlank(text)
                    ? AiMessage.from(toolExecutionRequests)
                    : AiMessage.from(text, toolExecutionRequests);

            return ChatResponse.builder()
                    .aiMessage(aiMessage)
                    .metadata(QwenChatResponseMetadata.builder()
                            .id(id)
                            .modelName(modelName)
                            .tokenUsage(new TokenUsage(inputTokenCount, outputTokenCount))
                            .finishReason(finishReason)
                            .searchInfo(searchInfo)
                            .build())
                    .build();
        }

        if (!isNullOrBlank(text)) {
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(text))
                    .metadata(QwenChatResponseMetadata.builder()
                            .id(id)
                            .modelName(modelName)
                            .tokenUsage(new TokenUsage(inputTokenCount, outputTokenCount))
                            .finishReason(finishReason)
                            .searchInfo(searchInfo)
                            .build())
                    .build();
        }

        return null;
    }

    private static class ToolExecutionRequestBuilder {

        private final StringBuilder idBuilder = new StringBuilder();
        private final StringBuilder nameBuilder = new StringBuilder();
        private final StringBuilder argumentsBuilder = new StringBuilder();
    }
}
