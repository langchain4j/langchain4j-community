package dev.langchain4j.community.model.client.chat.streaming;

import dev.langchain4j.Internal;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.community.model.client.chat.response.CohereChatResponseMetadata;
import dev.langchain4j.community.model.client.chat.response.CohereLogprobs;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.http.client.sse.ServerSentEvent;
import dev.langchain4j.http.client.sse.ServerSentEventContext;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.internal.ExceptionMapper;
import dev.langchain4j.internal.ToolCallBuilder;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.chat.response.StreamingHandle;
import dev.langchain4j.model.output.TokenUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static dev.langchain4j.community.model.client.chat.content.CohereContentType.TEXT;
import static dev.langchain4j.community.model.client.chat.content.CohereContentType.THINKING;
import static dev.langchain4j.community.model.util.CohereMapper.fromFinishReason;
import static dev.langchain4j.http.client.sse.ServerSentEventParsingHandleUtils.toStreamingHandle;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onCompleteToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialResponse;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialThinking;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.onPartialToolCall;
import static dev.langchain4j.internal.InternalStreamingChatResponseHandlerUtils.withLoggingExceptions;
import static dev.langchain4j.internal.Json.fromJson;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static java.util.Collections.synchronizedList;

@Internal
public class CohereServerSentEventListener implements ServerSentEventListener {

    private final String modelName;
    private final StreamingChatResponseHandler handler;
    private final StringBuilder textBuilder;
    private final StringBuilder thinkingBuilder;
    private final AtomicReference<String> responseId;
    private final ToolCallBuilder toolCallBuilder;
    private final List<CohereLogprobs> logprobs;

    volatile StreamingHandle streamingHandle;

    public CohereServerSentEventListener(String modelName,
                                         StreamingChatResponseHandler handler) {
        this.modelName = modelName;
        this.handler = handler;
        this.textBuilder = new StringBuilder();
        this.thinkingBuilder = new StringBuilder();
        this.responseId = new AtomicReference<>();
        this.toolCallBuilder = new ToolCallBuilder(-1);
        this.logprobs = synchronizedList(new ArrayList<>());
    }

    @Override
    public void onEvent(ServerSentEvent event, ServerSentEventContext context) {
        if (streamingHandle == null) {
            this.streamingHandle = toStreamingHandle(context.parsingHandle());
        }

        if ("[DONE]".equals(event.data())) {
            return;
        }

        if (event.event().equals("message-start")) {
            CohereStreamingStartData data = fromJson(event.data(), CohereStreamingStartData.class);
            handleMessageStart(data);
            return;
        }

        CohereStreamingData data = fromJson(event.data(), CohereStreamingData.class);

        if (event.event().equals("content-start")) {
            handleContentStart(data);
        } else if (event.event().equals("content-delta")) {
            handleContentDelta(data);
        } else if (event.event().equals("tool-call-start")) {
            handleStartToolCall(data);
        } else if (event.event().equals("tool-call-delta")) {
            handlePartialToolCall(data);
        } else if (event.event().equals("tool-call-end")){
            handleCompleteToolCall();
        } else if (event.event().equals("message-end")) {
            handleMessageEnd(data);
        }
    }

    private void handleMessageStart(CohereStreamingStartData data) {
        responseId.set(data.getId());
    }

    private void handleContentStart(CohereStreamingData data) {
        CohereStreamingContent content = data.getDelta().getMessage().getContent();

        if (content.getType() == TEXT && !isNullOrEmpty(content.getText())) {
            textBuilder.append(content.getText());
            onPartialResponse(handler, content.getText(), streamingHandle);
        }

        if (content.getType() == THINKING && !isNullOrEmpty(content.getThinking())) {
            thinkingBuilder.append(content.getThinking());
            onPartialThinking(handler, content.getThinking(), streamingHandle);
        }
    }

    private void handleContentDelta(CohereStreamingData data) {
        CohereStreamingContent message = data.getDelta().getMessage().getContent();

        if (!isNullOrEmpty(message.getText())) {
            textBuilder.append(message.getText());
            onPartialResponse(handler, message.getText(), streamingHandle);
        }

        if (!isNullOrEmpty(message.getThinking())) {
            thinkingBuilder.append(message.getThinking());
            onPartialThinking(handler, message.getThinking(), streamingHandle);
        }

        if (data.getLogprobs() != null) {
            logprobs.add(data.getLogprobs());
        }
    }

    private void handleStartToolCall(CohereStreamingData data) {
        String partialArguments = data.getDelta().getMessage().getToolCalls().getFunction().getArguments();
        toolCallBuilder.updateIndex(data.getIndex());
        toolCallBuilder.updateId(data.getDelta().getMessage().getToolCalls().getId());
        toolCallBuilder.updateName(data.getDelta().getMessage().getToolCalls().getFunction().getName());
        toolCallBuilder.appendArguments(partialArguments);
    }

    private void handlePartialToolCall(CohereStreamingData data) {
        String partialArguments = data.getDelta()
                .getMessage()
                .getToolCalls()
                .getFunction()
                .getArguments();

        toolCallBuilder.appendArguments(partialArguments);

        PartialToolCall partialToolCall = PartialToolCall.builder()
                .index(toolCallBuilder.index())
                .id(toolCallBuilder.id())
                .name(toolCallBuilder.name())
                .partialArguments(partialArguments)
                .build();

        onPartialToolCall(handler, partialToolCall, streamingHandle);
    }

    private void handleCompleteToolCall() {
        CompleteToolCall completeToolCall = toolCallBuilder.buildAndReset();

        if (completeToolCall.toolExecutionRequest().arguments().equals("{}")) {
            PartialToolCall partialToolRequest = PartialToolCall.builder()
                    .index(completeToolCall.index())
                    .id(completeToolCall.toolExecutionRequest().id())
                    .name(completeToolCall.toolExecutionRequest().name())
                    .partialArguments("{}")
                    .build();
            onPartialToolCall(handler, partialToolRequest, streamingHandle);
        }

        onCompleteToolCall(handler, completeToolCall);
    }

    private void handleMessageEnd(CohereStreamingData data) {
        ChatResponse response = build(data);
        onCompleteResponse(handler, response);
    }

    private ChatResponse build(CohereStreamingData data) {
        CohereChatResponseMetadata.Builder metadataBuilder = CohereChatResponseMetadata.builder()
                .billedUnits(data.getDelta().getUsage().getBilledUnits())
                .cachedTokens(data.getDelta().getUsage().getCachedTokens())
                .tokenUsage(new TokenUsage(
                        data.getDelta().getUsage().getTokens().getInputTokens(),
                        data.getDelta().getUsage().getTokens().getOutputTokens()))
                .id(responseId.get())
                .modelName(modelName)
                .finishReason(fromFinishReason(data.getDelta().getFinishReason()));

        if (!logprobs.isEmpty()) {
            metadataBuilder.logprobs(logprobs);
        }

        List<ToolExecutionRequest> toolExecutionRequests = List.of();
        if (toolCallBuilder.hasRequests()) {
            toolExecutionRequests = toolCallBuilder.allRequests();
        }

        AiMessage aiMessage = AiMessage.builder()
                .text(textBuilder.isEmpty() ? null : textBuilder.toString())
                .thinking(thinkingBuilder.isEmpty() ? null : thinkingBuilder.toString())
                .toolExecutionRequests(toolExecutionRequests)
                .build();

        return ChatResponse.builder()
                .aiMessage(aiMessage)
                .metadata(metadataBuilder.build())
                .build();
    }

    @Override
    public void onError(Throwable error) {
        RuntimeException mappedError = ExceptionMapper.DEFAULT.mapException(error);
        withLoggingExceptions(() -> handler.onError(mappedError));
    }
}

