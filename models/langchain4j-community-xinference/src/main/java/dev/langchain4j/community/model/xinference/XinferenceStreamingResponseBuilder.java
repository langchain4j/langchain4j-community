package dev.langchain4j.community.model.xinference;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.community.model.xinference.client.chat.ChatCompletionChoice;
import dev.langchain4j.community.model.xinference.client.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.xinference.client.chat.Delta;
import dev.langchain4j.community.model.xinference.client.chat.message.FunctionCall;
import dev.langchain4j.community.model.xinference.client.completion.CompletionChoice;
import dev.langchain4j.community.model.xinference.client.completion.CompletionResponse;
import dev.langchain4j.community.model.xinference.client.shared.CompletionUsage;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class needs to be thread safe because it is called when a streaming result comes back
 * and there is no guarantee that this thread will be the same as the one that initiated the request,
 * in fact it almost certainly won't be.
 */
public class XinferenceStreamingResponseBuilder {
    private final StringBuffer contentBuilder = new StringBuffer();
    private final AtomicReference<String> responseId = new AtomicReference<>();
    private final AtomicReference<String> responseModel = new AtomicReference<>();
    private volatile TokenUsage tokenUsage;
    private volatile FinishReason finishReason;
    private volatile List<ToolExecutionRequestBuilder> toolExecutionRequestList;

    public void append(ChatCompletionResponse partialResponse) {
        if (partialResponse == null) {
            return;
        }
        if (Utils.isNotNullOrBlank(partialResponse.getId())) {
            responseId.set(partialResponse.getId());
        }
        if (Utils.isNotNullOrBlank(partialResponse.getModel())) {
            responseModel.set(partialResponse.getModel());
        }
        final CompletionUsage usage = partialResponse.getUsage();
        if (usage != null) {
            this.tokenUsage = InternalXinferenceHelper.tokenUsageFrom(usage);
        }
        List<ChatCompletionChoice> choices = partialResponse.getChoices();
        if (choices == null || choices.isEmpty()) {
            return;
        }
        ChatCompletionChoice chatCompletionChoice = choices.get(0);
        if (chatCompletionChoice == null) {
            return;
        }
        String finishReason = chatCompletionChoice.getFinishReason();
        if (finishReason != null) {
            this.finishReason = InternalXinferenceHelper.finishReasonFrom(finishReason);
        }
        Delta delta = chatCompletionChoice.getDelta();
        if (delta == null) {
            return;
        }
        String content = delta.getContent();
        if (content != null) {
            contentBuilder.append(content);
            return;
        }
        if (!Utils.isNullOrEmpty(delta.getToolCalls())) {
            toolExecutionRequestList = delta.getToolCalls().stream().map(toolCall -> {
                final ToolExecutionRequestBuilder toolExecutionRequestBuilder = new ToolExecutionRequestBuilder();
                if (toolCall.getId() != null) {
                    toolExecutionRequestBuilder.idBuilder.append(toolCall.getId());
                }
                FunctionCall functionCall = toolCall.getFunction();
                if (functionCall.getName() != null) {
                    toolExecutionRequestBuilder.nameBuilder.append(functionCall.getName());
                }
                if (functionCall.getArguments() != null) {
                    toolExecutionRequestBuilder.argumentsBuilder.append(functionCall.getArguments());
                }
                return toolExecutionRequestBuilder;
            }).toList();
        }
    }

    public void append(CompletionResponse partialResponse) {
        if (partialResponse == null) {
            return;
        }
        CompletionUsage usage = partialResponse.getUsage();
        if (usage != null) {
            this.tokenUsage = InternalXinferenceHelper.tokenUsageFrom(usage);
        }
        List<CompletionChoice> choices = partialResponse.getChoices();
        if (choices == null || choices.isEmpty()) {
            return;
        }
        CompletionChoice completionChoice = choices.get(0);
        if (completionChoice == null) {
            return;
        }
        String finishReason = completionChoice.getFinishReason();
        if (finishReason != null) {
            this.finishReason = InternalXinferenceHelper.finishReasonFrom(finishReason);
        }
        String token = completionChoice.getText();
        if (token != null) {
            contentBuilder.append(token);
        }
    }

    public Response<AiMessage> build() {
        String text = contentBuilder.toString();
        if (!Utils.isNullOrEmpty(toolExecutionRequestList)) {
            final List<ToolExecutionRequest> list = toolExecutionRequestList.stream().map(it -> ToolExecutionRequest.builder()
                    .id(it.idBuilder.toString())
                    .name(it.nameBuilder.toString())
                    .arguments(it.argumentsBuilder.toString())
                    .build()).toList();
            AiMessage aiMessage = Utils.isNullOrBlank(text) ?
                    AiMessage.from(list) :
                    AiMessage.from(text, list);
            return Response.from(
                    aiMessage,
                    tokenUsage,
                    finishReason
            );
        }
        if (!Utils.isNullOrBlank(text)) {
            return Response.from(
                    AiMessage.from(text),
                    tokenUsage,
                    finishReason
            );
        }
        return null;
    }

    public String getResponseId() {
        return responseId.get();
    }

    public String getResponseModel() {
        return responseModel.get();
    }

    private static class ToolExecutionRequestBuilder {
        private final StringBuffer idBuilder = new StringBuffer();
        private final StringBuffer nameBuilder = new StringBuffer();
        private final StringBuffer argumentsBuilder = new StringBuffer();
    }
}
