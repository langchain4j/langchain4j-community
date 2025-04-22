package dev.langchain4j.community.model.xinference;

import static dev.langchain4j.community.model.xinference.InternalXinferenceHelper.toTools;
import static dev.langchain4j.community.model.xinference.InternalXinferenceHelper.toXinferenceMessages;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.xinference.client.XinferenceClient;
import dev.langchain4j.community.model.xinference.client.chat.ChatCompletionChoice;
import dev.langchain4j.community.model.xinference.client.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.xinference.client.chat.Delta;
import dev.langchain4j.community.model.xinference.client.shared.StreamOptions;
import dev.langchain4j.community.model.xinference.spi.XinferenceStreamingChatModelBuilderFactory;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XinferenceStreamingChatModel implements StreamingChatModel {

    private static final Logger log = LoggerFactory.getLogger(XinferenceStreamingChatModel.class);

    private final XinferenceClient client;
    private final String modelName;
    private final Double temperature;
    private final Double topP;
    private final List<String> stop;
    private final Integer maxTokens;
    private final Double presencePenalty;
    private final Double frequencyPenalty;
    private final Integer seed;
    private final String user;
    private final Object toolChoice;
    private final Boolean parallelToolCalls;
    private final List<ChatModelListener> listeners;

    public XinferenceStreamingChatModel(
            String baseUrl,
            String apiKey,
            String modelName,
            Double temperature,
            Double topP,
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
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        List<ChatMessage> messages = request.messages();
        ChatRequestParameters parameters = request.parameters();
        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder().stream(true)
                .streamOptions(StreamOptions.of(true))
                .model(modelName)
                .messages(toXinferenceMessages(messages))
                .temperature(temperature)
                .topP(topP)
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
            if (parameters.toolChoice() != null) {
                builder.toolChoice(parameters.toolChoice());
            }
        }

        ChatCompletionRequest xinferenceRequest = builder.build();

        Map<Object, Object> attributes = new ConcurrentHashMap<>();
        ChatModelRequestContext requestContext = new ChatModelRequestContext(request, provider(), attributes);
        listeners.forEach(listener -> {
            try {
                listener.onRequest(requestContext);
            } catch (Exception e) {
                log.warn("Exception while calling model listener", e);
            }
        });
        XinferenceStreamingResponseBuilder responseBuilder = new XinferenceStreamingResponseBuilder();
        client.chatCompletions(xinferenceRequest)
                .onPartialResponse(partialResponse -> {
                    responseBuilder.append(partialResponse);
                    List<ChatCompletionChoice> choices = partialResponse.getChoices();
                    if (!isNullOrEmpty(choices)) {
                        Delta delta = choices.get(0).getDelta();
                        String content = delta.getContent();
                        if (isNotNullOrEmpty(content)) {
                            handler.onPartialResponse(content);
                        }
                    }
                })
                .onComplete(() -> {
                    ChatResponse response = responseBuilder.build();
                    ChatModelResponseContext responseContext =
                            new ChatModelResponseContext(response, request, provider(), attributes);
                    listeners.forEach(listener -> {
                        try {
                            listener.onResponse(responseContext);
                        } catch (Exception e) {
                            log.warn("Exception while calling model listener", e);
                        }
                    });

                    handler.onCompleteResponse(response);
                })
                .onError(throwable -> {
                    ChatModelErrorContext errorContext =
                            new ChatModelErrorContext(throwable, request, provider(), attributes);

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

    public static XinferenceStreamingChatModelBuilder builder() {
        for (XinferenceStreamingChatModelBuilderFactory factory :
                loadFactories(XinferenceStreamingChatModelBuilderFactory.class)) {
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
            return new XinferenceStreamingChatModel(
                    this.baseUrl,
                    this.apiKey,
                    this.modelName,
                    this.temperature,
                    this.topP,
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
                    this.listeners);
        }
    }
}
