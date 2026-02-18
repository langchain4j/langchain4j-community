package dev.langchain4j.community.model;

import dev.langchain4j.community.model.client.CohereClient;
import dev.langchain4j.community.model.client.chat.CohereChatResponse;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.time.Duration;

import static dev.langchain4j.community.model.util.CohereMapper.fromCohereChatResponse;
import static dev.langchain4j.community.model.util.CohereMapper.toCohereChatRequest;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

public class CohereChatModel implements ChatModel {

    private final CohereClient client;
    private final ChatRequestParameters defaultRequestParameters;
    private final int maxRetries;


    public CohereChatModel(Builder builder) {
        this.client = CohereClient.builder()
                .baseUrl(getOrDefault(builder.baseUrl, "https://api.cohere.com/v2/"))
                .timeout(builder.timeout)
                .authToken(builder.authToken)
                .build();

        this.defaultRequestParameters = ChatRequestParameters.builder()
                .modelName(ensureNotBlank(builder.modelName, "Model name"))
                .build();

        this.maxRetries = getOrDefault(builder.maxRetries, 3);
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
       CohereChatResponse cohereResponse =
               withRetryMappingExceptions(() -> client.createMessage(toCohereChatRequest(chatRequest)), maxRetries);
       return fromCohereChatResponse(cohereResponse);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String baseUrl;
        private String authToken;
        private String modelName;
        private Duration timeout;
        private Integer maxRetries;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder authToken(String authToken) {
            this.authToken = authToken;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
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

        public CohereChatModel build() {
            return new CohereChatModel(this);
        }
    }
}
