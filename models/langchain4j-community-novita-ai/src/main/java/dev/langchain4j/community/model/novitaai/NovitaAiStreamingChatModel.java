package dev.langchain4j.community.model.novitaai;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.community.model.novitaai.client.NovitaAiClient;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequestValidator;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.community.model.novitaai.client.NovitaAiChatCompletionChoice;
import dev.langchain4j.community.model.novitaai.client.NovitaAiChatCompletionResponse;
import dev.langchain4j.community.model.novitaai.client.NovitaAiUsage;
import dev.langchain4j.community.model.novitaai.spi.NovitaAiStreamingChatModelBuilderFactory;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.community.model.novitaai.client.AbstractNovitaAIModel;
import dev.langchain4j.community.model.novitaai.client.NovitaAiChatCompletionRequest;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.internal.Exceptions.illegalArgument;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.community.model.novitaai.mapper.NovitaAiMapper.finishReasonFrom;
import static dev.langchain4j.community.model.novitaai.mapper.NovitaAiMapper.tokenUsageFrom;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * NovitaAI Chat model.
 */
@Slf4j
public class NovitaAiStreamingChatModel extends AbstractNovitaAIModel implements StreamingChatLanguageModel {

    private Boolean logResponses;

    /**
     * Constructor with Builder.
     *
     * @param builder
     *      builder.
     */
    public NovitaAiStreamingChatModel(Builder builder) {
        this(builder.modelName.getModelId(), builder.apiKey, builder.logResponses);
    }

    /**
     * Constructor with Builder.
     *
     * @param modelName
     *      model name
     * @param apiKey
     *     api token
     */
    public NovitaAiStreamingChatModel(String modelName, String apiKey, Boolean logResponses) {
        super(modelName, apiKey);
        this.logResponses = logResponses;
    }

    /**
     * Builder access.
     *
     * @return
     *      builder instance
     */
    public static Builder builder() {
        for (NovitaAiStreamingChatModelBuilderFactory factory : loadFactories(NovitaAiStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new Builder();
    }

    /**
     * Internal Builder.
     */
    public static class Builder {

        /**
         * ApiKey, preferred as string.
         */
        public String apiKey;
        /**
         * ModelName, preferred as enum for extensibility.
         */
        public NovitaAiChatModelName modelName;

        public Boolean logResponses;

        /**
         * Simple constructor.
         */
        public Builder() {
        }

        /**
         * Sets the apiKey for the Novita AI model builder.
         *
         * @param apiKey The apiKey to set.
         * @return The current instance of {@link Builder}.
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the model name for the Novita AI model builder.
         *
         * @param modelName The enum of the model to set.
         * @return The current instance of {@link Builder}.
         */
        public Builder modelName(NovitaAiChatModelName modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Sets the model name for the Novita AI model builder.
         *
         * @param modelName The string name of the model to set.
         * @return The current instance of {@link Builder}.
         */
        public Builder modelName(String modelName) {
            this.modelName = NovitaAiChatModelName.fromModelName(modelName);
            return this;
        }

        /**
         * Sets whether to log requests for the Novita AI model builder.
         *
         * @param logResponses The flag whether to log to set.
         * @return The current instance of {@link Builder}.
         */
        public Builder logResponses(Boolean logResponses) {
            if (logResponses == null) {
                logResponses = false;
            }
            this.logResponses = logResponses;
            return this;
        }

        /**
         * Builds a new instance of Novita AI Chat Model.
         *
         * @return A new instance of {@link NovitaAiChatModel}.
         */
        public NovitaAiStreamingChatModel build() {
            return new NovitaAiStreamingChatModel(this);
        }
    }

    @Override
    public void chat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ChatRequestValidator.validateMessages(chatRequest.messages());
        ChatRequestParameters parameters = chatRequest.parameters();
        ChatRequestValidator.validateParameters(parameters);
        ChatRequestValidator.validate(parameters.toolSpecifications());
        ChatRequestValidator.validate(parameters.toolChoice());
        ChatRequestValidator.validate(parameters.responseFormat());

        StreamingResponseHandler<AiMessage> legacyHandler = new StreamingResponseHandler<>() {

            @Override
            public void onNext(String token) {
                handler.onPartialResponse(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                ChatResponse chatResponse = ChatResponse.builder()
                        .aiMessage(response.content())
                        .metadata(ChatResponseMetadata.builder()
                                .tokenUsage(response.tokenUsage())
                                .finishReason(response.finishReason())
                                .build())
                        .build();
                handler.onCompleteResponse(chatResponse);
            }

            @Override
            public void onError(Throwable error) {
                handler.onError(error);
            }
        };
        generate(chatRequest.messages(), legacyHandler);
    }

    @Override
    public void chat(List<ChatMessage> messages, StreamingChatResponseHandler handler) {

        ChatRequest chatRequest = ChatRequest.builder()
                .messages(messages)
                .build();

        chat(chatRequest, handler);
    }

    private void generate(
            List<ChatMessage> messages,
            StreamingResponseHandler<AiMessage> handler) {
        ensureNotEmpty(messages, "messages");
        NovitaAiChatCompletionRequest request = new NovitaAiChatCompletionRequest();
        request.setModel(modelName);
        request.setStream(true);
        request.setMessages(messages.stream()
                .map(this::toMessage)
                .collect(Collectors.toList()));
        streamingChatCompletion(request, handler);
    }

    public void streamingChatCompletion(NovitaAiChatCompletionRequest request, StreamingResponseHandler<AiMessage> handler) {
        EventSourceListener eventSourceListener = new EventSourceListener() {
            final StringBuffer contentBuilder = new StringBuffer();
            List<ToolExecutionRequest> toolExecutionRequests;
            TokenUsage tokenUsage;
            FinishReason finishReason;

            @Override
            public void onOpen(@NotNull EventSource eventSource, okhttp3.@NotNull Response response) {
                if (logResponses) {
                    log.debug("onOpen()");
                }
            }

            @Override
            public void onEvent(@NotNull EventSource eventSource, String id, String type, @NotNull String data) {
                if (logResponses) {
                    log.debug("onEvent() {}", data);
                }
                if ("[DONE]".equals(data)) {
                    AiMessage aiMessage;
                    if (!isNullOrEmpty(toolExecutionRequests)) {
                        aiMessage = AiMessage.from(toolExecutionRequests);
                    } else {
                        aiMessage = AiMessage.from(contentBuilder.toString());
                    }

                    Response<AiMessage> response = Response.from(
                            aiMessage,
                            tokenUsage,
                            finishReason
                    );
                    handler.onComplete(response);
                } else {
                    try {
                        ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);
                        NovitaAiChatCompletionResponse chatCompletionResponse = OBJECT_MAPPER.readValue(data, NovitaAiChatCompletionResponse.class);
                        NovitaAiChatCompletionChoice choice = chatCompletionResponse.getChoices().get(0);
                        String chunk = choice.getMessage().getContent();
                        if (isNotNullOrEmpty(chunk)) {
                            contentBuilder.append(chunk);
                            handler.onNext(chunk);
                        }
                        NovitaAiUsage usageInfo = chatCompletionResponse.getUsage();
                        if (usageInfo != null) {
                            this.tokenUsage = tokenUsageFrom(usageInfo);
                        }
                        String finishReasonString = choice.getFinishReason();
                        if (finishReasonString != null) {
                            this.finishReason = finishReasonFrom(finishReasonString);
                        }
                    } catch (Exception e) {
                        handler.onError(e);
                        throw new RuntimeException(e);
                    }
                }
            }

            @Override
            public void onFailure(@NotNull EventSource eventSource, Throwable t, okhttp3.Response response) {
                if (logResponses) {
                    log.debug("onFailure()", t);
                }
                if (t != null) {
                    handler.onError(t);
                } else {
                    handler.onError(new RuntimeException(String.format("status code: %s; body: %s", response.code(), response.body())));
                }
            }

            @Override
            public void onClosed(@NotNull EventSource eventSource) {
                if (logResponses) {
                    log.debug("onClosed()");
                }
            }
        };
        EventSources.createFactory(NovitaAiClient.getOkHttpClient())
                .newEventSource(novitaClient.streamingChatCompletion(request).request(),
                        eventSourceListener);
    }

    /**
     * Mapping ChatMessage to NovitaAiChatCompletionRequest.Message
     *
     * @param message
     *      inbound message
     * @return
     *      message for request
     */
    private NovitaAiChatCompletionRequest.Message toMessage(ChatMessage message) {
        if (message instanceof final UserMessage userMessage) {
            return new NovitaAiChatCompletionRequest.Message(
                    NovitaAiChatCompletionRequest.MessageRole.user,
                    userMessage.singleText());
        }

        if (message instanceof final AiMessage aiMessage) {
            return new NovitaAiChatCompletionRequest.Message(
                    NovitaAiChatCompletionRequest.MessageRole.ai,
                    aiMessage.text());
        }

        if (message instanceof final SystemMessage systemMessage) {
            return new NovitaAiChatCompletionRequest.Message(
                    NovitaAiChatCompletionRequest.MessageRole.system,
                    systemMessage.text());
        }

        throw illegalArgument("Unknown message type: " + message.type());
    }
}
