package dev.langchain4j.community.model.minimax;

import static dev.langchain4j.community.model.minimax.InternalMiniMaxHelper.aiMessageFrom;
import static dev.langchain4j.community.model.minimax.InternalMiniMaxHelper.finishReasonFrom;
import static dev.langchain4j.community.model.minimax.InternalMiniMaxHelper.toTools;
import static dev.langchain4j.community.model.minimax.InternalMiniMaxHelper.toMiniMaxMessages;
import static dev.langchain4j.community.model.minimax.InternalMiniMaxHelper.tokenUsageFrom;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.minimax.client.MiniMaxClient;
import dev.langchain4j.community.model.minimax.client.chat.ChatCompletionChoice;
import dev.langchain4j.community.model.minimax.client.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.minimax.client.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.minimax.spi.MiniMaxChatModelBuilderFactory;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a MiniMax chat model that uses the OpenAI-compatible Chat Completions API.
 * <p>
 * MiniMax provides models such as MiniMax-M2.7, MiniMax-M2.5, and MiniMax-M2.5-highspeed
 * via the endpoint {@code https://api.minimax.io/v1}.
 *
 * @see MiniMaxChatModelName
 * @see <a href="https://platform.minimaxi.com/document/Models">MiniMax API Documentation</a>
 */
public class MiniMaxChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(MiniMaxChatModel.class);
    private static final String DEFAULT_BASE_URL = "https://api.minimax.io/";

    private final MiniMaxClient client;
    private final Integer maxRetries;
    private final List<ChatModelListener> listeners;
    private final ChatRequestParameters defaultRequestParameters;

    private final Integer seed;
    private final String user;
    private final Object toolChoice;
    private final Boolean parallelToolCalls;

    public MiniMaxChatModel(
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
        baseUrl = getOrDefault(baseUrl, DEFAULT_BASE_URL);
        timeout = getOrDefault(timeout, Duration.ofSeconds(60));
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.listeners = copy(listeners);

        this.client = MiniMaxClient.builder()
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
        this.defaultRequestParameters = ChatRequestParameters.builder()
                .modelName(ensureNotBlank(modelName, "modelName"))
                .temperature(temperature)
                .topP(topP)
                .stopSequences(stop)
                .maxOutputTokens(maxTokens)
                .presencePenalty(presencePenalty)
                .frequencyPenalty(frequencyPenalty)
                .build();

        this.seed = seed;
        this.user = user;
        this.toolChoice = toolChoice;
        this.parallelToolCalls = parallelToolCalls;
    }

    public static MiniMaxChatModelBuilder builder() {
        for (MiniMaxChatModelBuilderFactory factory : loadFactories(MiniMaxChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new MiniMaxChatModelBuilder();
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ChatResponse doChat(ChatRequest request) {
        List<ChatMessage> messages = request.messages();
        ChatRequestParameters parameters = request.parameters();
        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder()
                .model(parameters.modelName())
                .messages(toMiniMaxMessages(messages))
                .temperature(parameters.temperature())
                .topP(parameters.topP())
                .stop(parameters.stopSequences())
                .maxTokens(parameters.maxOutputTokens())
                .presencePenalty(parameters.presencePenalty())
                .frequencyPenalty(parameters.frequencyPenalty())
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

        ChatCompletionRequest miniMaxRequest = builder.build();
        ChatCompletionResponse chatCompletionResponse =
                withRetry(() -> client.chatCompletions(miniMaxRequest).execute(), maxRetries);

        ChatCompletionChoice completionChoice =
                chatCompletionResponse.getChoices().get(0);

        return ChatResponse.builder()
                .aiMessage(aiMessageFrom(completionChoice.getMessage()))
                .metadata(ChatResponseMetadata.builder()
                        .id(chatCompletionResponse.getId())
                        .modelName(request.modelName())
                        .finishReason(finishReasonFrom(completionChoice.getFinishReason()))
                        .tokenUsage(tokenUsageFrom(chatCompletionResponse.getUsage()))
                        .build())
                .build();
    }

    public static class MiniMaxChatModelBuilder {
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

        public MiniMaxChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public MiniMaxChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public MiniMaxChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public MiniMaxChatModelBuilder modelName(MiniMaxChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public MiniMaxChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public MiniMaxChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public MiniMaxChatModelBuilder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public MiniMaxChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public MiniMaxChatModelBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public MiniMaxChatModelBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public MiniMaxChatModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public MiniMaxChatModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        public MiniMaxChatModelBuilder toolChoice(Object toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public MiniMaxChatModelBuilder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public MiniMaxChatModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public MiniMaxChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public MiniMaxChatModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public MiniMaxChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public MiniMaxChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public MiniMaxChatModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public MiniMaxChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public MiniMaxChatModel build() {
            return new MiniMaxChatModel(
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
