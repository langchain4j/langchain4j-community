package dev.langchain4j.community.model.xinference;

import static dev.langchain4j.community.model.xinference.InternalXinferenceHelper.aiMessageFrom;
import static dev.langchain4j.community.model.xinference.InternalXinferenceHelper.finishReasonFrom;
import static dev.langchain4j.community.model.xinference.InternalXinferenceHelper.toTools;
import static dev.langchain4j.community.model.xinference.InternalXinferenceHelper.toXinferenceMessages;
import static dev.langchain4j.community.model.xinference.InternalXinferenceHelper.tokenUsageFrom;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.xinference.client.XinferenceClient;
import dev.langchain4j.community.model.xinference.client.XinferenceHttpException;
import dev.langchain4j.community.model.xinference.client.chat.ChatCompletionChoice;
import dev.langchain4j.community.model.xinference.client.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.xinference.client.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.xinference.spi.XinferenceChatModelBuilderFactory;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XinferenceChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(XinferenceChatModel.class);

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
    private final Integer maxRetries;
    private final List<ChatModelListener> listeners;

    public XinferenceChatModel(
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
            Integer maxRetries,
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
                .logResponses(logResponses)
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
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.listeners = getOrDefault(listeners, List.of());
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        List<ChatMessage> messages = request.messages();
        ChatRequestParameters parameters = request.parameters();
        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder()
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

        try {
            ChatCompletionResponse chatCompletionResponse =
                    withRetry(() -> client.chatCompletions(xinferenceRequest).execute(), maxRetries);

            ChatCompletionChoice completionChoice =
                    chatCompletionResponse.getChoices().get(0);
            ChatResponse response = ChatResponse.builder()
                    .aiMessage(aiMessageFrom(completionChoice.getMessage()))
                    .tokenUsage(tokenUsageFrom(chatCompletionResponse.getUsage()))
                    .finishReason(finishReasonFrom(completionChoice.getFinishReason()))
                    .build();

            ChatModelResponseContext responseContext =
                    new ChatModelResponseContext(response, request, provider(), attributes);
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
            ChatModelErrorContext errorContext = new ChatModelErrorContext(error, request, provider(), attributes);

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
            return new XinferenceChatModel(
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
                    this.maxRetries,
                    this.timeout,
                    this.proxy,
                    this.logRequests,
                    this.logResponses,
                    this.customHeaders,
                    this.listeners);
        }
    }
}
