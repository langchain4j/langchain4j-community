package dev.langchain4j.community.model;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.client.CohereClient;
import dev.langchain4j.community.model.client.chat.CohereChatRequest;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.langchain4j.community.model.util.CohereMapper.toCohereChatRequest;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.model.chat.request.DefaultChatRequestParameters.EMPTY;
import static java.util.Arrays.asList;

public class CohereStreamingChatModel implements StreamingChatModel {

    private final CohereClient client;
    private final ChatRequestParameters defaultRequestParameters;
    private final List<ChatModelListener> listeners;
    private final String thinkingType;
    private final Integer thinkingTokenBudget;
    private final Set<Capability> supportedCapabilities;

    private CohereStreamingChatModel(CohereStreamingChatModelBuilder builder) {
        this.client = CohereClient.builder()
                .baseUrl("https://api.cohere.com/v2/")
                .timeout(builder.timeout)
                .authToken(builder.apiKey)
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
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
                .toolChoice(getOrDefault(builder.toolChoice, commonParameters.toolChoice()))
                .responseFormat(getOrDefault(builder.responseFormat, commonParameters.responseFormat()))
                .build();

        this.listeners = copy(builder.listeners);
        this.thinkingType = builder.thinkingType;
        this.thinkingTokenBudget = builder.thinkingTokenBudget;
        this.supportedCapabilities = copy(builder.supportedCapabilities);
    }

    @Override
    public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {

        // TODO: This should be refactored, this function might grow in the future
        // Consider using a subclass of parameters.
        CohereChatRequest cohereChatRequest = toCohereChatRequest(
                request.toBuilder().parameters(defaultRequestParameters.overrideWith(request.parameters())).build(),
                thinkingType, thinkingTokenBudget, true);

        client.createStreamingMessage(cohereChatRequest, handler);
    }

    public static CohereStreamingChatModelBuilder builder() {
        return new CohereStreamingChatModelBuilder();
    }

    public static class CohereStreamingChatModelBuilder {

        private Duration timeout;
        private String apiKey;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Integer maxOutputTokens;
        private List<String> stopSequences;
        private List<ToolSpecification> toolSpecifications;
        private ToolChoice toolChoice;
        private ResponseFormat responseFormat;
        private String thinkingType;
        private Integer thinkingTokenBudget;

        private ChatRequestParameters defaultRequestParameters;
        private List<ChatModelListener> listeners;
        private Set<Capability> supportedCapabilities;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        public CohereStreamingChatModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public CohereStreamingChatModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public CohereStreamingChatModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public CohereStreamingChatModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public CohereStreamingChatModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public CohereStreamingChatModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public CohereStreamingChatModelBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public CohereStreamingChatModelBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public CohereStreamingChatModelBuilder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public CohereStreamingChatModelBuilder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public CohereStreamingChatModelBuilder toolSpecifications(ToolSpecification... toolSpecifications) {
            return toolSpecifications(asList(toolSpecifications));
        }

        public CohereStreamingChatModelBuilder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications = toolSpecifications;
            return this;
        }

        public CohereStreamingChatModelBuilder toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public CohereStreamingChatModelBuilder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public CohereStreamingChatModelBuilder thinkingType(String thinkingType) {
            this.thinkingType = thinkingType;
            return this;
        }

        public CohereStreamingChatModelBuilder thinkingTokenBudget(Integer thinkingTokenBudget) {
            this.thinkingTokenBudget = thinkingTokenBudget;
            return this;
        }

        public CohereStreamingChatModelBuilder defaultRequestParameters(ChatRequestParameters defaultRequestParameters) {
            this.defaultRequestParameters = defaultRequestParameters;
            return this;
        }

        public CohereStreamingChatModelBuilder listeners(ChatModelListener... listeners) {
            return listeners(asList(listeners));
        }

        public CohereStreamingChatModelBuilder listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return this;
        }

        public CohereStreamingChatModelBuilder supportedCapabilities(Capability... supportedCapabilities) {
            this.supportedCapabilities = Arrays.stream(supportedCapabilities).collect(Collectors.toSet());
            return this;
        }

        public CohereStreamingChatModelBuilder supportedCapabilities(Set<Capability> supportedCapabilities) {
            this.supportedCapabilities = supportedCapabilities;
            return this;
        }

        public CohereStreamingChatModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public CohereStreamingChatModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public CohereStreamingChatModelBuilder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public CohereStreamingChatModel build() { return new CohereStreamingChatModel(this); }
    }

    @Override
    public List<ChatModelListener> listeners() { return listeners; }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public Set<Capability> supportedCapabilities() { return supportedCapabilities; }
}
