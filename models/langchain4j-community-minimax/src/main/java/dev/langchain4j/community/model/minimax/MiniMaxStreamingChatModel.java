package dev.langchain4j.community.model.minimax;

import static dev.langchain4j.community.model.minimax.InternalMiniMaxHelper.toTools;
import static dev.langchain4j.community.model.minimax.InternalMiniMaxHelper.toMiniMaxMessages;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.minimax.client.MiniMaxClient;
import dev.langchain4j.community.model.minimax.client.chat.ChatCompletionChoice;
import dev.langchain4j.community.model.minimax.client.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.minimax.client.chat.Delta;
import dev.langchain4j.community.model.minimax.client.shared.StreamOptions;
import dev.langchain4j.community.model.minimax.spi.MiniMaxStreamingChatModelBuilderFactory;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a MiniMax streaming chat model that uses the OpenAI-compatible Chat Completions API.
 *
 * @see MiniMaxChatModelName
 * @see <a href="https://platform.minimaxi.com/document/Models">MiniMax API Documentation</a>
 */
public class MiniMaxStreamingChatModel implements StreamingChatModel {

    private static final Logger log = LoggerFactory.getLogger(MiniMaxStreamingChatModel.class);
    private static final String DEFAULT_BASE_URL = "https://api.minimax.io/";

    private final MiniMaxClient client;
    private final List<ChatModelListener> listeners;
    private final ChatRequestParameters defaultRequestParameters;

    private final Integer seed;
    private final String user;
    private final Object toolChoice;
    private final Boolean parallelToolCalls;

    public MiniMaxStreamingChatModel(
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
        baseUrl = getOrDefault(baseUrl, DEFAULT_BASE_URL);
        timeout = getOrDefault(timeout, Duration.ofSeconds(60));
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
                .logStreamingResponses(logResponses)
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

    public static MiniMaxStreamingChatModelBuilder builder() {
        for (MiniMaxStreamingChatModelBuilderFactory factory :
                loadFactories(MiniMaxStreamingChatModelBuilderFactory.class)) {
            return factory.get();
        }
        return new MiniMaxStreamingChatModelBuilder();
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
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
        List<ChatMessage> messages = request.messages();
        ChatRequestParameters parameters = request.parameters();
        List<ToolSpecification> toolSpecifications = parameters.toolSpecifications();
        ChatCompletionRequest.Builder builder = ChatCompletionRequest.builder().stream(true)
                .streamOptions(StreamOptions.of(true))
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
        MiniMaxStreamingResponseBuilder responseBuilder = new MiniMaxStreamingResponseBuilder();
        client.chatCompletions(miniMaxRequest)
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
                .onComplete(() -> handler.onCompleteResponse(responseBuilder.build()))
                .onError(handler::onError)
                .execute();
    }

    public static class MiniMaxStreamingChatModelBuilder {

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

        public MiniMaxStreamingChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public MiniMaxStreamingChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public MiniMaxStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public MiniMaxStreamingChatModelBuilder modelName(MiniMaxChatModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        public MiniMaxStreamingChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public MiniMaxStreamingChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public MiniMaxStreamingChatModelBuilder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public MiniMaxStreamingChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public MiniMaxStreamingChatModelBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public MiniMaxStreamingChatModelBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public MiniMaxStreamingChatModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public MiniMaxStreamingChatModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        public MiniMaxStreamingChatModelBuilder toolChoice(Object toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public MiniMaxStreamingChatModelBuilder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public MiniMaxStreamingChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public MiniMaxStreamingChatModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public MiniMaxStreamingChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public MiniMaxStreamingChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public MiniMaxStreamingChatModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public MiniMaxStreamingChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public MiniMaxStreamingChatModel build() {
            return new MiniMaxStreamingChatModel(
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
