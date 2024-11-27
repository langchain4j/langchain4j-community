package dev.langchain4j.community.model.xinference;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.xinference.client.XinferenceClient;
import dev.langchain4j.community.model.xinference.client.XinferenceHttpException;
import dev.langchain4j.community.model.xinference.client.chat.ChatCompletionChoice;
import dev.langchain4j.community.model.xinference.client.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.xinference.client.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.xinference.spi.XinferenceChatModelBuilderFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequest;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponse;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class XinferenceChatModel implements ChatLanguageModel {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(XinferenceChatModel.class);
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
    private final Integer maxRetries;
    private final List<ChatModelListener> listeners;

    public XinferenceChatModel(String baseUrl,
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
                               Integer maxRetries,
                               Duration timeout,
                               Proxy proxy,
                               Boolean logRequests,
                               Boolean logResponses,
                               Map<String, String> customHeaders,
                               List<ChatModelListener> listeners) {
        timeout = Utils.getOrDefault(timeout, Duration.ofSeconds(60));

        this.client = XinferenceClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .customHeaders(customHeaders)
                .build();

        this.modelName = ValidationUtils.ensureNotBlank(modelName, "modelName");
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
        this.maxRetries = Utils.getOrDefault(maxRetries, 3);
        this.listeners = Utils.getOrDefault(listeners, List.of());
    }

    @Override
    public Response<AiMessage> generate(final List<ChatMessage> list) {
        return generate(list, null, null);

    }

    @Override
    public Response<AiMessage> generate(final List<ChatMessage> messages, final List<ToolSpecification> toolSpecifications) {
        return generate(messages, toolSpecifications, null);
    }

    @Override
    public Response<AiMessage> generate(final List<ChatMessage> messages, final ToolSpecification toolSpecification) {
        return generate(messages, null, toolSpecification);
    }

    @Override
    public ChatResponse chat(final ChatRequest request) {
        final Response<AiMessage> response = generate(request.messages(), request.toolSpecifications(), null);
        return ChatResponse.builder()
                .aiMessage(response.content())
                .tokenUsage(response.tokenUsage())
                .finishReason(response.finishReason())
                .build();
    }

    private Response<AiMessage> generate(List<ChatMessage> messages,
                                         List<ToolSpecification> toolSpecifications,
                                         ToolSpecification toolThatMustBeExecuted) {
        final ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder()
                .model(modelName)
                .messages(InternalXinferenceHelper.toXinferenceMessages(messages))
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
            builder.tools(InternalXinferenceHelper.toTools(toolSpecifications));
        }

        if (toolThatMustBeExecuted != null) {
            if (Utils.isNullOrEmpty(toolSpecifications)) {
                builder.tools(InternalXinferenceHelper.toTools(List.of(toolThatMustBeExecuted)));
            }

            builder.toolChoice(InternalXinferenceHelper.toToolChoice(toolThatMustBeExecuted));
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

        try {
            ChatCompletionResponse chatCompletionResponse = withRetry(() -> client.chatCompletions(request).execute(), maxRetries);

            final ChatCompletionChoice completionChoice = chatCompletionResponse.getChoices().get(0);
            Response<AiMessage> response = Response.from(
                    InternalXinferenceHelper.aiMessageFrom(completionChoice.getMessage()),
                    InternalXinferenceHelper.tokenUsageFrom(chatCompletionResponse.getUsage()),
                    InternalXinferenceHelper.finishReasonFrom(completionChoice.getFinishReason())
            );

            ChatModelResponse modelListenerResponse = ChatModelResponse.builder()
                    .id(chatCompletionResponse.getId())
                    .model(chatCompletionResponse.getModel())
                    .tokenUsage(response.tokenUsage())
                    .finishReason(response.finishReason())
                    .aiMessage(response.content())
                    .build();

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

            return response;
        } catch (RuntimeException e) {

            Throwable error;
            if (e.getCause() instanceof XinferenceHttpException) {
                error = e.getCause();
            } else {
                error = e;
            }

            ChatModelErrorContext errorContext = new ChatModelErrorContext(
                    error,
                    modelListenerRequest,
                    null,
                    attributes
            );

            listeners.forEach(listener -> {
                try {
                    listener.onError(errorContext);
                } catch (Exception e2) {
                    log.warn("Exception while calling model listener", e2);
                }
            });

            throw e;
        }
    }

    public static XinferenceChatModelBuilder builder() {
        for (XinferenceChatModelBuilderFactory factory : loadFactories(XinferenceChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new XinferenceChatModelBuilder();
    }

    public static class XinferenceChatModelBuilder {
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
        private Integer maxRetries;
        private Duration timeout;
        private Proxy proxy;
        private Boolean logRequests;
        private Boolean logResponses;
        private Map<String, String> customHeaders;
        private List<ChatModelListener> listeners;

        public XinferenceChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public XinferenceChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public XinferenceChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public XinferenceChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public XinferenceChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public XinferenceChatModelBuilder n(Integer n) {
            this.n = n;
            return this;
        }

        public XinferenceChatModelBuilder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public XinferenceChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public XinferenceChatModelBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public XinferenceChatModelBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public XinferenceChatModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public XinferenceChatModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        public XinferenceChatModelBuilder toolChoice(Object toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public XinferenceChatModelBuilder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public XinferenceChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public XinferenceChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public XinferenceChatModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public XinferenceChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public XinferenceChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public XinferenceChatModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public XinferenceChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public XinferenceChatModel build() {
            return new XinferenceChatModel(this.baseUrl,
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
                    this.maxRetries,
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
