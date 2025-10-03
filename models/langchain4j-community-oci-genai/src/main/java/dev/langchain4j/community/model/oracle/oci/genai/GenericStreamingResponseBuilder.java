package dev.langchain4j.community.model.oracle.oci.genai;

import com.oracle.bmc.generativeaiinference.model.AssistantMessage;
import com.oracle.bmc.generativeaiinference.model.ChatChoice;
import com.oracle.bmc.generativeaiinference.model.FunctionCall;
import com.oracle.bmc.generativeaiinference.model.TextContent;
import com.oracle.bmc.generativeaiinference.model.ToolCall;
import com.oracle.bmc.http.client.Serializer;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class GenericStreamingResponseBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenericStreamingResponseBuilder.class);
    private static final Serializer SERIALIZER = Serializer.getDefault();
    private static final String SSE_PREFIX = "data: ";
    private static final int SSE_PREFIX_LENGTH = SSE_PREFIX.length();
    private static final int INIT = 0;
    private static final int CONTENT = 1;
    private static final int TOOL_CALL_INIT = 2;
    private static final int TOOL_CALL_CONTINUE = 3;
    private static final int FINISH = 4;
    private final LongAdder order = new LongAdder();
    private final String modelName;
    private final StreamingChatResponseHandler handler;
    private final StringBuilder content = new StringBuilder();
    private final Map<String, ChunkedToolCallBuilder> toolCallMap = new HashMap<>();
    private String finishReason = null;
    private String lastToolCallId = null;
    private ChatChoice chatChoice = null;

    private int state = INIT;

    GenericStreamingResponseBuilder(String modelName, StreamingChatResponseHandler handler) {
        this.modelName = modelName;
        this.handler = handler;
    }

    ChatResponse build() {
        var chatResponse = OciGenAiChatModel.map(chatChoice, modelName, null);
        this.completeResponse(chatResponse);
        return chatResponse;
    }

    /**
     * SSE lines example:
     * <pre>{@code
     * data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":""}]},"pad":"aaaaa"}
     * data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","id":"chatcmpl-tool-e5f86a0","name":"sqrt"}]},"pad":"aaaaaa"}
     * data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":"{\"arg0\": \""}]},"pad":"aaa"}
     * data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":"16\"}"}]},"pad":"aaa"}
     * data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":""}]},"pad":"aaaaaaa"}
     * data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":""}]},"pad":"aa"}
     * data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","id":"chatcmpl-tool-13ee961b","name":"extractMagicalNumber"}]},"pad":"aaaaaa"}
     * data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":"{\"arg1\": \""}]},"pad":"aaa"}
     * data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":"778\""}]},"pad":"aaaaaaaaa"}
     * data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":", \"arg0\": \""}]},"pad":"aaaa"}
     * data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":"556\"}"}]},"pad":"a"}
     * data: {"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":""}]},"finishReason":"tool_calls","pad":"aaaaaaaaa"}
     * }</pre>
     */
    void parseAndAdd(String sseLine) {
        try {
            var partialChoice = SERIALIZER.readValue(sseLine.substring(SSE_PREFIX_LENGTH), ChatChoice.class);
            if (partialChoice == null) {
                return;
            }

            if (Utils.isNotNullOrBlank(partialChoice.getFinishReason())) {
                finishReason = partialChoice.getFinishReason();
                moveState(FINISH);
            }

            var message = partialChoice.getMessage();
            if (message == null) return;

            order.increment();
            StringBuilder partialContent = new StringBuilder();

            if (message instanceof AssistantMessage assistantMessage) {
                var contents =
                        Optional.ofNullable(assistantMessage.getContent()).orElseGet(List::of);
                for (var cont : contents) {
                    if (cont instanceof TextContent textContent) {
                        moveState(CONTENT);
                        var text = textContent.getText();
                        content.append(text);
                        partialContent.append(text);
                    } else {
                        throw new IllegalStateException(
                                "Only TextContent is supported in streaming chat but got " + cont);
                    }
                }
                var toolCalls =
                        Optional.ofNullable(assistantMessage.getToolCalls()).orElseGet(List::of);

                for (ToolCall toolCall : toolCalls) {
                    if (toolCall instanceof FunctionCall functionCall) {
                        String id = functionCall.getId();
                        ChunkedToolCallBuilder toolCallBuilder;
                        if (id != null) {
                            moveState(TOOL_CALL_INIT);
                            // starting new tool call, finish previous one
                            if (lastToolCallId != null) {
                                completeToolCall(toolCallMap.get(lastToolCallId).toCompleteToolCall());
                            }
                            lastToolCallId = id;
                        } else {
                            moveState(TOOL_CALL_CONTINUE);
                        }

                        int index = partialChoice.getIndex() == null ? 0 : partialChoice.getIndex();

                        toolCallBuilder = toolCallMap
                                .computeIfAbsent(
                                        lastToolCallId, ChunkedToolCallBuilder.createFnc(order.intValue(), index))
                                .append(functionCall);

                        toolCallBuilder
                                .toPartialToolCall(functionCall.getArguments())
                                .ifPresent(this::partialToolCall);

                    } else {
                        LOGGER.warn("Unexpected tool call type: {}", toolCall);
                    }
                }

            } else {
                LOGGER.warn("Unexpected message type: {}", message);
            }

            var resultBuilder = ChatChoice.builder();
            if (!partialContent.isEmpty()) {
                partialResponse(partialContent.toString());
            }
            if (state == FINISH) {
                if (lastToolCallId != null) {
                    // finish last tool call if any
                    var lastChunkedToolCallBuilder = toolCallMap.get(lastToolCallId);
                    handler.onCompleteToolCall(lastChunkedToolCallBuilder.toCompleteToolCall());
                }
                resultBuilder.finishReason(finishReason);
            }

            resultBuilder.message(AssistantMessage.builder()
                    .content(List.of(com.oracle.bmc.generativeaiinference.model.TextContent.builder()
                            .text(content.toString())
                            .build()))
                    .toolCalls(toolCallMap.values().stream()
                            .sorted()
                            .map(ChunkedToolCallBuilder::build)
                            .toList())
                    .build());

            this.chatChoice = resultBuilder.build();
        } catch (IOException e) {
            LOGGER.error("Error parsing SSE line: {}", sseLine, e);
            handler.onError(e);
            throw new UncheckedIOException(e);
        }
    }

    private int moveState(int state) {
        if (this.state == FINISH) {
            return this.state;
        }
        return this.state = state;
    }

    private void partialToolCall(PartialToolCall partialToolCall) {
        try {
            handler.onPartialToolCall(partialToolCall);
        } catch (Exception e) {
            try {
                handler.onError(e);
            } catch (Exception userException) {
                LOGGER.error("Error in partialToolCall handler", userException);
            }
        }
    }

    private void completeToolCall(CompleteToolCall completeToolCall) {
        try {
            handler.onCompleteToolCall(completeToolCall);
        } catch (Exception e) {
            try {
                handler.onError(e);
            } catch (Exception userException) {
                LOGGER.error("Error in completeToolCall handler", userException);
            }
        }
    }

    private void partialResponse(String partialResponse) {
        try {
            handler.onPartialResponse(partialResponse);
        } catch (Exception e) {
            try {
                handler.onError(e);
            } catch (Exception userException) {
                LOGGER.error("Error in error handler", userException);
            }
        }
    }

    private void completeResponse(ChatResponse chatResponse) {
        try {
            handler.onCompleteResponse(chatResponse);
        } catch (Exception e) {
            try {
                handler.onError(e);
            } catch (Exception userException) {
                LOGGER.error("Error in complete response handler", userException);
            }
        }
    }

    private static class ChunkedToolCallBuilder implements Comparable<ChunkedToolCallBuilder> {

        private final int index;
        private final String id;
        private final int order;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        ChunkedToolCallBuilder(int order, int index, String id) {
            this.index = index;
            this.id = id;
            this.order = order;
        }

        static Function<String, ChunkedToolCallBuilder> createFnc(int order, int index) {
            return s -> new ChunkedToolCallBuilder(order, index, s);
        }

        @Override
        public int compareTo(ChunkedToolCallBuilder o) {
            return Integer.compare(order, o.order);
        }

        ChunkedToolCallBuilder append(ToolCall toolCall) {
            if (toolCall instanceof FunctionCall functionCall) {
                var name = functionCall.getName();
                if (!Utils.isNullOrEmpty(name)) {
                    this.name = name;
                }
                var argChunk = functionCall.getArguments();
                if (!Utils.isNullOrEmpty(argChunk)) {
                    arguments.append(argChunk);
                }
                return this;
            } else {
                throw new IllegalStateException("Only FunctionCall is supported in streaming chat but got " + toolCall);
            }
        }

        Optional<PartialToolCall> toPartialToolCall(String chunk) {
            if (Utils.isNullOrBlank(chunk)) return Optional.empty();
            return Optional.of(PartialToolCall.builder()
                    .id(id)
                    .index(index)
                    .name(name)
                    .partialArguments(chunk)
                    .build());
        }

        CompleteToolCall toCompleteToolCall() {
            return new CompleteToolCall(
                    index,
                    ToolExecutionRequest.builder()
                            .id(id)
                            .arguments(arguments.toString())
                            .name(name)
                            .build());
        }

        ToolCall build() {
            return FunctionCall.builder()
                    .id(id)
                    .name(name)
                    .arguments(arguments.isEmpty() ? "{}" : arguments.toString())
                    .build();
        }
    }
}
