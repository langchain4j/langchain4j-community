package dev.langchain4j.community.model.oracle.oci.genai;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.generativeaiinference.model.BaseChatRequest;
import com.oracle.bmc.generativeaiinference.model.ChatDetails;
import com.oracle.bmc.generativeaiinference.model.ServingMode;
import com.oracle.bmc.http.client.Serializer;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class BaseChatModel<T extends BaseChatModel<T>> implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(BaseChatModel.class);

    private final GenerativeAiInferenceClient client;
    private final String compartmentId;

    BaseChatModel(Builder<?, ?> builder) {
        this.compartmentId = builder.compartmentId();
        this.client = builder.genAiClient();
    }

    @Override
    public void close() {
        client.close();
    }

    /**
     * Validates the given chat request.
     *
     * @param lc4jReq the chat request that is to be validated
     */
    protected void validateRequest(ChatRequest lc4jReq) {
        // noop
    }

    /**
     * Default request parameters.
     *
     * @return an instance of {@link ChatRequestParameters} containing the default request parameters.
     */
    public abstract ChatRequestParameters defaultRequestParameters();

    /**
     * Sends given OCI BMC request over OCI SDK client and returns received OCI BMC response.
     *
     * @param chatRequest OCI BMC request
     * @param servingMode Serving mode with model name information
     * @return OCI BMC response
     */
    protected com.oracle.bmc.generativeaiinference.responses.ChatResponse ociChat(
            BaseChatRequest chatRequest, ServingMode servingMode) {
        LOGGER.debug("Chat Request: {}", chatRequest);

        var details = ChatDetails.builder()
                .servingMode(servingMode)
                .compartmentId(compartmentId)
                .chatRequest(chatRequest)
                .build();

        var request = com.oracle.bmc.generativeaiinference.requests.ChatRequest.builder()
                .chatDetails(details)
                .build();

        var chatResponse = client.chat(request);
        LOGGER.debug("Chat Response: {}", chatResponse);
        return chatResponse;
    }

    static <P> void setIfNotNull(P value, Consumer<P> consumer) {
        setIfNotNull(value, Function.identity(), consumer);
    }

    static <P, R> void setIfNotNull(P value, Function<P, R> mapper, Consumer<R> consumer) {
        if ((value instanceof Collection<?> col && col.isEmpty()) || value == null) {
            return;
        }
        var mapped = mapper.apply(value);
        if (mapped != null) {
            consumer.accept(mapped);
        }
    }

    static String toJson(Object object) {
        try {
            return Serializer.getDefault().writeValueAsString(object);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return Serializer.getDefault().readValue(json, clazz);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    abstract static class Builder<T extends BaseChatModel<T>, B extends Builder<T, B>> {
        private Region region;
        private BasicAuthenticationDetailsProvider authProvider;
        private String compartmentId;
        private String modelName;
        private Integer maxTokens;
        private Integer topK;
        private Double topP;
        private Double temperature;
        private Double frequencyPenalty;
        private Double presencePenalty;
        private Integer seed;
        private List<String> stop;
        private GenerativeAiInferenceClient genAiClient;
        private List<ChatModelListener> listeners = List.of();
        private ServingMode.ServingType servingType = ServingMode.ServingType.OnDemand;
        private ChatRequestParameters defaultRequestParameters =
                ChatRequestParameters.builder().build();

        protected Builder() {}

        abstract B self();

        /**
         * A {@link dev.langchain4j.model.chat.ChatModel} listeners that listen for requests, responses and errors.
         *
         * @param listeners listeners of requests, responses and errors
         * @return builder
         */
        public B listeners(List<ChatModelListener> listeners) {
            this.listeners = listeners;
            return self();
        }

        List<ChatModelListener> listeners() {
            return listeners;
        }

        /**
         * Sets default common {@link ChatRequestParameters}.
         * <br>
         * When a parameter is set via an individual builder method (e.g., {@link #modelName(String)}),
         * its value takes precedence over the same parameter set via {@link ChatRequestParameters}.
         *
         * @param parameters default parameters to be used
         * @return builder
         */
        public B defaultRequestParameters(ChatRequestParameters parameters) {
            this.defaultRequestParameters = parameters;
            return self();
        }

        ChatRequestParameters defaultRequestParameters() {
            return this.defaultRequestParameters;
        }

        /**
         * Manually configured OCI SDK GenAi client.
         * When set, values provided with {@link Builder#region(Region)}
         * and {@link Builder#authProvider(BasicAuthenticationDetailsProvider)}
         * are ignored.
         *
         * @param genAiClient manually configured OCI SDK GenAi client
         * @return builder
         */
        public B genAiClient(GenerativeAiInferenceClient genAiClient) {
            this.genAiClient = genAiClient;
            return self();
        }

        GenerativeAiInferenceClient genAiClient() {
            if (this.genAiClient == null && this.authProvider == null) {
                throw new IllegalArgumentException("GenAi client or authentication provider needs to be provided.");
            }

            if (this.genAiClient == null) {
                var clientBuilder = GenerativeAiInferenceClient.builder();
                if (this.region() != null) {
                    clientBuilder.region(this.region());
                }
                this.genAiClient = clientBuilder.build(this.authProvider());
            }

            return this.genAiClient;
        }

        /**
         * OCI Region to connect the client to.
         *
         * @param region OCI Region
         * @return builder
         */
        public B region(Region region) {
            this.region = region;
            return self();
        }

        Region region() {
            return region;
        }

        /**
         * OCI SDK Authentication provider.
         *
         * @param authProvider OCI SDK Authentication provider
         * @return builder
         */
        public B authProvider(BasicAuthenticationDetailsProvider authProvider) {
            this.authProvider = authProvider;
            return self();
        }

        BasicAuthenticationDetailsProvider authProvider() {
            return authProvider;
        }

        /**
         * OCID of OCI Compartment with the model.
         *
         * @param compartmentId Compartment OCID
         * @return builder
         */
        public B compartmentId(String compartmentId) {
            this.compartmentId = compartmentId;
            return self();
        }

        String compartmentId() {
            return compartmentId;
        }

        /**
         * Name or OCID of the model for
         * {@link com.oracle.bmc.generativeaiinference.model.ServingMode.ServingType#OnDemand} servingType
         * or endpoint ID for
         * {@link com.oracle.bmc.generativeaiinference.model.ServingMode.ServingType#Dedicated} servingType.
         *
         * @param modelName Model name or it's OCID
         * @return builder
         */
        public B modelName(String modelName) {
            this.modelName = modelName;
            return self();
        }

        String modelName() {
            return modelName;
        }

        /**
         * The model's serving mode, which is either on-demand serving or dedicated serving.
         * OnDemand is a default.
         * <ul>
         * <li>{@link com.oracle.bmc.generativeaiinference.model.ServingMode.ServingType#OnDemand} servingType.</li>
         * <li>{@link com.oracle.bmc.generativeaiinference.model.ServingMode.ServingType#Dedicated} servingType.</li>
         * </ul>
         * @param servingType Serving mode
         * @return builder
         */
        public B servingType(ServingMode.ServingType servingType) {
            this.servingType = servingType;
            return self();
        }

        ServingMode.ServingType servingType() {
            return servingType;
        }

        /**
         * Maximum number of tokens that can be generated per output sequence.
         *
         * @param maxTokens Maximum number of tokens per output sequence
         * @return builder
         */
        public B maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return self();
        }

        Integer maxTokens() {
            return maxTokens;
        }

        /**
         * An integer that sets up the model to use only the top k most likely tokens in the generated output.
         * A higher k introduces more randomness into the output making the output text sound more natural.
         * Default value is -1 which means to consider all tokens. Setting to 0 disables this method and considers all tokens.
         * If also using top p, then the model considers only the top tokens whose probabilities add up to p percent
         * and ignores the rest of the k tokens.
         * For example, if k is 20, but the probabilities of the top 10 add up to .75, then only the top 10 tokens are chosen.
         *
         * @param topK Value to set
         * @return builder
         */
        public B topK(Integer topK) {
            this.topK = topK;
            return self();
        }

        Integer topK() {
            return topK;
        }

        /**
         * If set to a probability 0.0 &lt; p &lt; 1.0, it ensures that only the most likely tokens,
         * with total probability mass of p, are considered for generation at each step.
         * To eliminate tokens with low likelihood, assign p a minimum percentage for the next token's likelihood.
         * For example, when p is set to 0.75, the model eliminates the bottom 25 percent for the next token.
         * Set to 1 to consider all tokens and set to 0 to disable. If both k and p are enabled, p acts after k.
         *
         * @param topP Value to set
         * @return builder
         */
        public B topP(Double topP) {
            this.topP = topP;
            return self();
        }

        Double topP() {
            return topP;
        }

        /**
         * A number that sets the randomness of the generated output. A lower temperature means a less random generations.
         * Use lower numbers for tasks with a correct answer such as question answering or summarizing.
         * High temperatures can generate hallucinations or factually incorrect information.
         * Start with temperatures lower than 1.0 and increase the temperature for more creative outputs,
         * as you regenerate the prompts to refine the outputs.
         *
         * @param temperature Value to set
         * @return builder
         */
        public B temperature(Double temperature) {
            this.temperature = temperature;
            return self();
        }

        Double temperature() {
            return temperature;
        }

        /**
         * To reduce repetitiveness of generated tokens,
         * this number penalizes new tokens based on their frequency in the generated text so far.
         * Values &gt; 0 encourage the model to use new tokens and values &lt; 0 encourage the model to repeat tokens.
         * Set to 0 to disable.
         *
         * @param frequencyPenalty Value to set
         * @return builder
         */
        public B frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return self();
        }

        Double frequencyPenalty() {
            return frequencyPenalty;
        }

        /**
         * To reduce repetitiveness of generated tokens,
         * this number penalizes new tokens based on whether they've appeared in the generated text so far.
         * Values &gt; 0 encourage the model to use new tokens and values &lt; 0 encourage the model to repeat tokens.
         * Similar to frequency penalty, a penalty is applied to previously present tokens,
         * except that this penalty is applied equally to all tokens that have already appeared,
         * regardless of how many times they've appeared. Set to 0 to disable.
         *
         * @param presencePenalty Value to set
         * @return builder
         */
        public B presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return self();
        }

        Double presencePenalty() {
            return presencePenalty;
        }

        /**
         * If specified, the backend will make the best effort to sample tokens deterministically,
         * so that repeated requests with the same seed and parameters yield the same result.
         * However, determinism cannot be fully guaranteed.
         *
         * @param seed Value to set
         * @return builder
         */
        public B seed(Integer seed) {
            this.seed = seed;
            return self();
        }

        Integer seed() {
            return seed;
        }

        /**
         * List of strings that stop the generation if they are generated for the response text.
         * The returned output will not contain the stop strings.
         *
         * @param stop Value to set
         * @return builder
         */
        public B stop(List<String> stop) {
            this.stop = stop;
            return self();
        }

        List<String> stop() {
            if (stop == null) {
                return defaultRequestParameters().stopSequences();
            }
            return stop;
        }

        /**
         * Build new instance.
         *
         * @return the instance
         */
        public abstract T build();
    }
}
