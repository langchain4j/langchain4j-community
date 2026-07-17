package dev.langchain4j.community.model;

import static dev.langchain4j.community.model.util.CohereInternalHelper.toCohereStreamingChatRequest;
import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.util.Arrays.asList;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.client.CohereChatRequestParameters;
import dev.langchain4j.community.model.client.CohereClient;
import dev.langchain4j.community.model.client.CohereSafetyMode;
import dev.langchain4j.community.model.client.CohereThinkingType;
import dev.langchain4j.community.model.client.chat.CohereChatRequest;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * Represents a Cohere LLM with a chat API.
 * <br/>
 * This implementation is based on Cohere's V2 Chat API.
 * <p>
 * The model's response is streamed token by token and should be handled with {@link StreamingResponseHandler}.
 *
 * @see <a href="https://docs.cohere.com/reference/chat">Cohere's V2 Chat API specification</a>
 * @see <a href="https://docs.cohere.com/v2/docs/migrating-v1-to-v2">Cohere's API migration guide</a>
 * @see <a href="https://docs.cohere.com/v2/docs/chat-api">Cohere's text generation guides</a>
 */
public class CohereStreamingChatModel implements StreamingChatModel {

    private final CohereClient client;
    private final ChatRequestParameters defaultRequestParameters;
    private final List<ChatModelListener> listeners;
    private final Set<Capability> supportedCapabilities;

    private CohereStreamingChatModel(CohereStreamingChatModelBuilder builder) {
        this.client = CohereClient.builder()
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.cohere.com/v2/"))
                .timeout(builder.timeout)
                .authToken(builder.apiKey)
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .build();

        ChatRequestParameters commonParameters =
                getOrDefault(builder.defaultRequestParameters, DefaultChatRequestParameters.EMPTY);
        CohereChatRequestParameters cohereDefaultParameters =
                builder.defaultRequestParameters instanceof CohereChatRequestParameters cohereChatRequestParameters
                        ? cohereChatRequestParameters
                        : CohereChatRequestParameters.EMPTY;

        this.defaultRequestParameters = CohereChatRequestParameters.builder()
                // Common parameters
                .modelName(getOrDefault(builder.modelName, commonParameters.modelName()))
                .temperature(getOrDefault(builder.temperature, commonParameters.temperature()))
                .topP(getOrDefault(builder.topP, commonParameters.topP()))
                .topK(getOrDefault(builder.topK, commonParameters.topK()))
                .frequencyPenalty(getOrDefault(builder.frequencyPenalty, commonParameters.frequencyPenalty()))
                .presencePenalty(getOrDefault(builder.presencePenalty, commonParameters.presencePenalty()))
                .maxOutputTokens(getOrDefault(builder.maxTokens, commonParameters.maxOutputTokens()))
                .stopSequences(getOrDefault(copy(builder.stopSequences), commonParameters.stopSequences()))
                .toolSpecifications(
                        getOrDefault(copy(builder.toolSpecifications), commonParameters.toolSpecifications()))
                .toolChoice(getOrDefault(builder.toolChoice, commonParameters.toolChoice()))
                .responseFormat(getOrDefault(builder.responseFormat, commonParameters.responseFormat()))
                // Cohere specific parameters
                .thinkingType(getOrDefault(builder.thinkingType, cohereDefaultParameters.thinkingType()))
                .thinkingTokenBudget(
                        getOrDefault(builder.thinkingTokenBudget, cohereDefaultParameters.thinkingTokenBudget()))
                .safetyMode(getOrDefault(builder.safetyMode, cohereDefaultParameters.safetyMode()))
                .priority(getOrDefault(builder.priority, cohereDefaultParameters.priority()))
                .seed(getOrDefault(builder.seed, cohereDefaultParameters.seed()))
                .logprobs(getOrDefault(builder.logprobs, cohereDefaultParameters.logprobs()))
                .strictTools(getOrDefault(builder.strictTools, cohereDefaultParameters.strictTools()))
                .build();

        this.listeners = copy(builder.listeners);
        this.supportedCapabilities = copy(builder.supportedCapabilities);
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        CohereChatRequestParameters parameters =
                CohereChatRequestParameters.builder().build().defaultedBy(chatRequest.parameters());

        CohereChatRequest cohereChatRequest = toCohereStreamingChatRequest(chatRequest.messages(), parameters);

        client.createStreamingMessage(cohereChatRequest, handler);
    }

    public static CohereStreamingChatModelBuilder builder() {
        return new CohereStreamingChatModelBuilder();
    }

    public static class CohereStreamingChatModelBuilder {

        private Duration timeout;
        private String apiKey;
        private String baseUrl;
        private String modelName;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Integer maxTokens;
        private List<String> stopSequences;
        private List<ToolSpecification> toolSpecifications;
        private ToolChoice toolChoice;
        private ResponseFormat responseFormat;
        private CohereThinkingType thinkingType;
        private Integer thinkingTokenBudget;
        private CohereSafetyMode safetyMode;
        private Integer priority;
        private Integer seed;
        private Boolean logprobs;
        private Boolean strictTools;

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

        public CohereStreamingChatModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
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

        public CohereStreamingChatModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
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

        /**
         * If set to {@link CohereThinkingType#DISABLED}, it will disable
         * <a href="https://docs.cohere.com/reference/chat-stream#request.body.thinking">thinking</a>
         * for reasoning models.
         */
        public CohereStreamingChatModelBuilder thinkingType(CohereThinkingType thinkingType) {
            this.thinkingType = thinkingType;
            return this;
        }

        /**
         * The maximum number of tokens the model can use for
         * <a href="https://docs.cohere.com/reference/chat-stream#request.body.thinking">thinking</a>.
         */
        public CohereStreamingChatModelBuilder thinkingTokenBudget(Integer thinkingTokenBudget) {
            this.thinkingTokenBudget = thinkingTokenBudget;
            return this;
        }

        /**
         * The <a href="https://docs.cohere.com/reference/chat-stream#request.body.safety_mode">safety instruction</a>
         * to be inserted into the prompt of the model.
         */
        public CohereStreamingChatModelBuilder safetyMode(CohereSafetyMode safetyMode) {
            this.safetyMode = safetyMode;
            return this;
        }

        /**
         * Controls how early the request is handled. When the system is under load,
         * the higher this parameter the more likely the request will be processed first,
         * as specified <a href="https://docs.cohere.com/reference/chat#request.body.priority">here</a>.
         */
        public CohereStreamingChatModelBuilder priority(Integer priority) {
            this.priority = priority;
            return this;
        }

        /**
         * If specified, the backend will make the best effort to sample tokens deterministically,
         * as specified <a href="https://docs.cohere.com/reference/chat#request.body.seed">here</a>.
         */
        public CohereStreamingChatModelBuilder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        /**
         * If set to {@code true}, the log probabilities of the generated tokens will be included in the response,
         * as specified <a href="https://docs.cohere.com/reference/chat-stream#request.body.logprobs">here</a>.
         */
        public CohereStreamingChatModelBuilder logprobs(Boolean logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        /**
         * If set to {@code true}, the model will be forced to follow the tool definitions strictly, as
         * specified <a href="https://docs.cohere.com/reference/chat-stream#request.body.strict_tools">here</a>.
         */
        public CohereStreamingChatModelBuilder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
            return this;
        }

        public CohereStreamingChatModelBuilder defaultRequestParameters(
                ChatRequestParameters defaultRequestParameters) {
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

        public CohereStreamingChatModel build() {
            return new CohereStreamingChatModel(this);
        }
    }

    @Override
    public List<ChatModelListener> listeners() {
        return listeners;
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return supportedCapabilities;
    }
}
