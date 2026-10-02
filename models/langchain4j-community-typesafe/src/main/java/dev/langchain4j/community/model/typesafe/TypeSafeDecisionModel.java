package dev.langchain4j.community.model.typesafe;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import dev.langchain4j.community.model.systemone.SystemOneDecisionModel;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.DecisionRequest;
import dev.langchain4j.model.decision.DecisionRequestParameters;
import dev.langchain4j.model.decision.DecisionResponse;

/** A {@link DecisionModel} backed by TypeSafe AI's System One API. */
@Experimental
public final class TypeSafeDecisionModel implements DecisionModel {

    /** Default TypeSafe API root used by the official SDKs. */
    public static final String DEFAULT_BASE_URL = "https://api.typesafe.ai";

    /** Default System One model used by the official SDKs. */
    public static final String DEFAULT_MODEL_NAME = "jev-latest";

    private final SystemOneDecisionModel delegate;
    private final DecisionRequestParameters defaultRequestParameters;

    private TypeSafeDecisionModel(Builder builder) {
        String apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        String baseUrl = ensureNotBlank(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL), "baseUrl");
        String modelName = ensureNotBlank(getOrDefault(builder.modelName, DEFAULT_MODEL_NAME), "modelName");
        this.defaultRequestParameters =
                DecisionRequestParameters.builder().modelName(modelName).build();
        SystemOneDecisionModel.Builder delegateBuilder =
                SystemOneDecisionModel.builder().apiKey(apiKey).baseUrl(baseUrl).modelName(modelName);
        if (builder.httpClientBuilder != null) {
            delegateBuilder.httpClientBuilder(builder.httpClientBuilder);
        }
        this.delegate = delegateBuilder.build();
    }

    /** Creates a builder for a TypeSafe-backed structured decision model. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public DecisionResponse decide(DecisionRequest request) {
        return delegate.decide(request);
    }

    @Override
    public DecisionRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    /** Builder for {@link TypeSafeDecisionModel}. */
    public static final class Builder {
        private String apiKey;
        private String baseUrl;
        private String modelName;
        private HttpClientBuilder httpClientBuilder;

        /** Sets the TypeSafe API key. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** Sets the TypeSafe API root. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** Sets the default model name. */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /** Sets the HTTP client builder used to create the transport. */
        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /** Builds the model. */
        public TypeSafeDecisionModel build() {
            return new TypeSafeDecisionModel(this);
        }
    }
}
