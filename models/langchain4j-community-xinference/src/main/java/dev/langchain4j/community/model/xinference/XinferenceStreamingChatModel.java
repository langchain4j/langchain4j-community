package dev.langchain4j.community.model.xinference;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.xinference.client.XinferenceClient;
import dev.langchain4j.community.model.xinference.client.chat.ChatCompletionChoice;
import dev.langchain4j.community.model.xinference.client.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.xinference.client.chat.Delta;
import dev.langchain4j.community.model.xinference.client.shared.StreamOptions;
import dev.langchain4j.community.model.xinference.spi.XinferenceStreamingChatModelBuilderFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.community.model.xinference.InternalXinferenceHelper.toToolChoice;
import static dev.langchain4j.community.model.xinference.InternalXinferenceHelper.toTools;
import static dev.langchain4j.community.model.xinference.InternalXinferenceHelper.toXinferenceMessages;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class XinferenceStreamingChatModel implements StreamingChatLanguageModel {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(XinferenceStreamingChatModel.class);
    private final XinferenceClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final Integer n;
    private final List<String> stop;
    private final Integer maxTokens;
    private final Double presencePenalty;
    private final Double frequencyPenalty;
    private final Integer seed;
    private final String user;
    private final Object toolChoice;
    private final Boolean parallelToolCalls;
    private final List<ChatModelListener> listeners;

    public XinferenceStreamingChatModel(String baseUrl,
                                        String apiKey,
                                        String modelName,
                                        Double temperature,
                                        Double topP,
                                        Integer n,
                                        List<String> stop,
                                        Integer maxTokens,
                                        Double presencePenalty,
                                        Double frequencyPenalty,
                                        Integer seed,
                                        String user,
                                        Object toolChoice,
                                        Boolean parallelToolCalls,
                                        Duration timeout,
                                        Proxy proxy,
                                        Boolean logRequests,
                                        Boolean logResponses,
                                        Map<String, String> customHeaders,
                                        List<ChatModelListener> listeners) {
        timeout = getOrDefault(timeout, Duration.ofSeconds(60));

        this.client = XinferenceClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logStreamingResponses(logResponses)
                .customHeaders(customHeaders)
                .build();

        this.modelName = ensureNotBlank(modelName, "modelName");
        this.temperature = temperature;
        this.topP = topP;
        this.n = n;
        this.stop = stop;
        this.maxTokens = maxTokens;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
        this.seed = seed;
        this.user = user;
        this.toolChoice = toolChoice;
        this.parallelToolCalls = parallelToolCalls;
        this.listeners = getOrDefault(listeners, List.of());
    }

    @Override
    public void generate(final List<ChatMessage> list, final StreamingResponseHandler<AiMessage> handler) {
        generate(list, null, null, handler);
    }

    @Override
    public void generate(final List<ChatMessage> messages, final List<ToolSpecification> toolSpecifications, final StreamingResponseHandler<AiMessage> handler) {
        generate(messages, toolSpecifications, null, handler);
    }

    @Override
    public void generate(final List<ChatMessage> messages, final ToolSpecification toolSpecification, final StreamingResponseHandler<AiMessage> handler) {
        generate(messages, null, toolSpecification, handler);
    }

    private void generate(List<ChatMessage> messages,
                          List<ToolSpecification> toolSpecifications,
                          ToolSpecification toolThatMustBeExecuted,
                          StreamingResponseHandler<AiMessage> handler) {
        final ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder()
                .stream(true)
                .streamOptions(StreamOptions.of(true))
                .model(modelName)
                .messages(toXinferenceMessages(messages))
                .temperature(temperature)
                .topP(topP)
                .n(n)
                .stop(stop)
                .maxTokens(maxTokens)
                .presencePenalty(presencePenalty)
                .frequencyPenalty(frequencyPenalty)
                .user(user)
                .seed(seed)
                .toolChoice(toolChoice)
                .parallelToolCalls(parallelToolCalls);

        if (toolSpecifications != null && !toolSpecifications.isEmpty()) {
            builder.tools(toTools(toolSpecifications));
        }

        if (toolThatMustBeExecuted != null) {
            if (isNullOrEmpty(toolSpecifications)) {
                builder.tools(toTools(List.of(toolThatMustBeExecuted)));
            }
            builder.toolChoice(toToolChoice(toolThatMustBeExecuted));
        }

        final ChatCompletionRequest request = builder.build();

        ChatModelRequest modelListenerRequest = ChatModelRequest.builder()
                .model(request.getModel())
                .temperature(request.getTemperature())
                .topP(request.getTopP())
                .maxTokens(request.getMaxTokens())
                .messages(messages)
                .toolSpecifications(toolSpecifications)
                .build();
        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(modelListenerRequest, attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });
        XinferenceStreamingResponseBuilder responseBuilder = new XinferenceStreamingResponseBuilder();
        client.chatCompletions(request)
                .onPartialResponse(partialResponse -> {
                    responseBuilder.append(partialResponse);
                    final List<ChatCompletionChoice> choices = partialResponse.getChoices();
                    if (!isNullOrEmpty(choices)) {
                        for (final ChatCompletionChoice choice : choices) {
                            final Delta delta = choice.getDelta();
                            final String content = delta.getContent();
                            if (isNotNullOrEmpty(content)) {
                                handler.onNext(content);
                            }
                        }
                    }
                })
                .onComplete(() -> {
                    Response<AiMessage> response = responseBuilder.build();
                    ChatModelResponse modelListenerResponse = createModelListenerResponse(responseBuilder.getResponseId(), responseBuilder.getResponseModel(), response);
                    ChatModelResponseContext responseContext = new ChatModelResponseContext(
                            modelListenerResponse,
                            modelListenerRequest,
                            attributes
                    );
                    listeners.forEach(listener -> {
                        try {
                            listener.onResponse(responseContext);
                        } catch (Exception e) {
                            log.warn("Exception while calling model listener", e);
                        }
                    });

                    handler.onComplete(response);
                })
                .onError(throwable -> {
                    Response<AiMessage> response = responseBuilder.build();
                    ChatModelResponse modelListenerPartialResponse = createModelListenerResponse(responseBuilder.getResponseId(), responseBuilder.getResponseModel(), response);

                    ChatModelErrorContext errorContext = new ChatModelErrorContext(
                            throwable,
                            modelListenerRequest,
                            modelListenerPartialResponse,
                            attributes
                    );

                    listeners.forEach(listener -> {
                        try {
                            listener.onError(errorContext);
                        } catch (Exception e) {
                            log.warn("Exception while calling model listener", e);
                        }
                    });

                    handler.onError(throwable);
                })
                .execute();

    }

    private static ChatModelResponse createModelListenerResponse(String responseId, String responseModel, Response<AiMessage> response) {
        if (response == null) {
            return null;
        }
        return ChatModelResponse.builder()
                .id(responseId)
                .model(responseModel)
                .tokenUsage(response.tokenUsage())
                .finishReason(response.finishReason())
                .aiMessage(response.content())
                .build();
    }

    public static XinferenceStreamingChatModelBuilder builder() {
        for (XinferenceStreamingChatModelBuilderFactory factory : loadFactories(XinferenceStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new XinferenceStreamingChatModelBuilder();
    }

    public static class XinferenceStreamingChatModelBuilder {
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer n;
        private List<String> stop;
        private Integer maxTokens;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Integer seed;
        private String user;
        private Object toolChoice;
        private Boolean parallelToolCalls;
        private Duration timeout;
        private Proxy proxy;
        private Boolean logRequests;
        private Boolean logResponses;
        private Map<String, String> customHeaders;
        private List<ChatModelListener> listeners;

        public XinferenceStreamingChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public XinferenceStreamingChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public XinferenceStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public XinferenceStreamingChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public XinferenceStreamingChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public XinferenceStreamingChatModelBuilder n(Integer n) {
            this.n = n;
            return this;
        }

        public XinferenceStreamingChatModelBuilder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public XinferenceStreamingChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public XinferenceStreamingChatModelBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public XinferenceStreamingChatModelBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public XinferenceStreamingChatModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public XinferenceStreamingChatModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        public XinferenceStreamingChatModelBuilder toolChoice(Object toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public XinferenceStreamingChatModelBuilder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public XinferenceStreamingChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public XinferenceStreamingChatModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public XinferenceStreamingChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public XinferenceStreamingChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public XinferenceStreamingChatModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public XinferenceStreamingChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public XinferenceStreamingChatModel build() {
            return new XinferenceStreamingChatModel(this.baseUrl,
                    this.apiKey,
                    this.modelName,
                    this.temperature,
                    this.topP,
                    this.n,
                    this.stop,
                    this.maxTokens,
                    this.presencePenalty,
                    this.frequencyPenalty,
                    this.seed,
                    this.user,
                    this.toolChoice,
                    this.parallelToolCalls,
                    this.timeout,
                    this.proxy,
                    this.logRequests,
                    this.logResponses,
                    this.customHeaders,
                    this.listeners
            );
        }
    }
}
