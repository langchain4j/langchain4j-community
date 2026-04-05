package dev.langchain4j.community.model;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.client.CohereClient;
import dev.langchain4j.community.model.client.chat.CohereChatRequest;
import dev.langchain4j.community.model.client.chat.response.CohereChatResponse;
import dev.langchain4j.community.model.util.CohereChatRequestParameters;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static dev.langchain4j.community.model.util.CohereInternalHelper.fromCohereChatResponse;
import static dev.langchain4j.community.model.util.CohereInternalHelper.toCohereChatRequest;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.chat.request.DefaultChatRequestParameters.EMPTY;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toSet;

public class CohereChatModel implements ChatModel {

    private final CohereClient client;
    private final ChatRequestParameters defaultRequestParameters;
    private final List<ChatModelListener> listeners;
    private final int maxRetries;
    private final String thinkingType;
    private final Integer thinkingTokenBudget;
    private final String safetyMode;
    private final Integer priority;
    private final Integer seed;
    private final Boolean logprobs;
    private final Set<Capability> supportedCapabilities;

    public CohereChatModel(Builder builder) {
        this.client = CohereClient.builder()
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.cohere.com/v2/"))
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .logger(builder.logger)
                .timeout(builder.timeout)
                .authToken(builder.apiKey)
                .build();

        ChatRequestParameters commonParameters = getOrDefault(builder.defaultRequestParameters, EMPTY);

        this.defaultRequestParameters = ChatRequestParameters.builder()
                .modelName(getOrDefault(builder.modelName, commonParameters.modelName()))
                .temperature(getOrDefault(builder.temperature, commonParameters.temperature()))
                .topP(getOrDefault(builder.topP, commonParameters.topP()))
                .topK(getOrDefault(builder.topK, commonParameters.topK()))
                .frequencyPenalty(getOrDefault(builder.frequencyPenalty, commonParameters.frequencyPenalty()))
                .presencePenalty(getOrDefault(builder.presencePenalty, commonParameters.presencePenalty()))
                .maxOutputTokens(getOrDefault(builder.maxOutputTokens, commonParameters.maxOutputTokens()))
                .stopSequences(getOrDefault(copy(builder.stopSequences), commonParameters.stopSequences()))
                .toolSpecifications(getOrDefault(copy(builder.toolSpecifications), commonParameters.toolSpecifications()))
                .toolChoice(builder.toolChoice)
                .responseFormat(getOrDefault(builder.responseFormat, commonParameters.responseFormat()))
                .build();

        this.thinkingType = builder.thinkingType;
        this.thinkingTokenBudget = builder.thinkingTokenBudget;
        this.safetyMode = builder.safetyMode;
        this.priority = builder.priority;
        this.seed = builder.seed;
        this.logprobs = builder.logprobs;

        this.maxRetries = getOrDefault(builder.maxRetries, 3);
        this.listeners = copy(builder.listeners);
        this.supportedCapabilities = copy(builder.supportedCapabilities);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        CohereChatRequestParameters parameters = CohereChatRequestParameters.builder()
                .thinkingType(thinkingType)
                .thinkingTokenBudget(thinkingTokenBudget)
                .safetyMode(safetyMode)
                .priority(priority)
                .seed(seed)
                .logprobs(logprobs)
                .build()
                .defaultedBy(chatRequest.parameters());

        CohereChatRequest cohereChatRequest = toCohereChatRequest(chatRequest.messages(), parameters);

       CohereChatResponse cohereResponse =
               withRetryMappingExceptions(() -> client.createMessage(cohereChatRequest), maxRetries);

       return fromCohereChatResponse(cohereResponse, cohereChatRequest.getModel());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String baseUrl;
        private String apiKey;
        private Duration timeout;
        private Integer maxRetries;
        private List<ChatModelListener> listeners;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private Integer maxOutputTokens;
        private List<String> stopSequences;
        private List<ToolSpecification> toolSpecifications;
        private ToolChoice toolChoice;
        private ResponseFormat responseFormat;
        private String thinkingType;
        private Integer thinkingTokenBudget;
        private String safetyMode;
        private Integer priority;
        private Integer seed;
        private Boolean logprobs;

        private ChatRequestParameters defaultRequestParameters;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private Set<Capability> supportedCapabilities;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder authToken(String authToken) {
            this.apiKey = authToken;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder listeners(ChatModelListener... listeners) {
            return listeners(asList(listeners));
        }

        public Builder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder stopSequences(String... stopSequences) {
            this.stopSequences = asList(stopSequences);
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public Builder toolSpecification(ToolSpecification... toolSpecifications) {
            this.toolSpecifications = asList(toolSpecifications);
            return this;
        }

        public Builder toolSpecification(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        public Builder toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public Builder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder thinkingType(String thinkingType) {
            this.thinkingType = thinkingType;
            return this;
        }

        public Builder thinkingTokenBudget(Integer thinkingTokenBudget) {
            this.thinkingTokenBudget = thinkingTokenBudget;
            return this;
        }

        /**
         * Selects the <a href="https://docs.cohere.com/reference/chat-stream#request.body.safety_mode">safety instruction</a>
         * inserted into the prompt of the model.
         */
        public Builder safetyMode(String safetyMode) {
            this.safetyMode = safetyMode;
            return this;
        }

        /**
         * Controls how early the request is handled. When the system is under load,
         * the higher this parameter the more likely the request will be processed first,
         * as specified <a href="https://docs.cohere.com/reference/chat#request.body.priority">here</a>.
         */
        public Builder priority(Integer priority) {
            this.priority = priority;
            return this;
        }

        /**
         * If specified, the backend will make the best effort to sample tokens deterministically,
         * as specified <a href="https://docs.cohere.com/reference/chat#request.body.seed">here</a>.
         */
        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        /**
         * If set to {@code true}, the log probabilities of the generated tokens will be included in the response.
         */
        public Builder logprobs(Boolean logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public Builder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder supportedCapabilities(Capability... supportedCapabilities) {
            this.supportedCapabilities = Arrays.stream(supportedCapabilities).collect(toSet());
            return this;
        }

        public Builder supportedCapabilities(Set<Capability> supportedCapabilities) {
            this.supportedCapabilities = supportedCapabilities;
            return this;
        }

        public CohereChatModel build() {
            return new CohereChatModel(this);
        }
    }

    @Override
    public List<ChatModelListener> listeners() { return listeners; }

    @Override
    public Set<Capability> supportedCapabilities() { return supportedCapabilities; }
}
