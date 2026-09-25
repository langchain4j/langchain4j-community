package dev.langchain4j.community.model.typesafe;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.Experimental;
import dev.langchain4j.community.model.systemone.SystemOneStructuredDecisionModel;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.structureddecision.StructuredDecisionModel;
import dev.langchain4j.model.structureddecision.StructuredDecisionRequest;
import dev.langchain4j.model.structureddecision.StructuredDecisionRequestParameters;
import dev.langchain4j.model.structureddecision.StructuredDecisionResponse;

/** A {@link StructuredDecisionModel} backed by TypeSafe AI's System One API. */
@Experimental
public final class TypeSafeStructuredDecisionModel implements StructuredDecisionModel {

    /** Default TypeSafe API root used by the official SDKs. */
    public static final String DEFAULT_BASE_URL = "https://api.typesafe.ai";

    /** Default System One model used by the official SDKs. */
    public static final String DEFAULT_MODEL_NAME = "jev-latest";

    private final SystemOneStructuredDecisionModel delegate;
    private final StructuredDecisionRequestParameters defaultRequestParameters;

    private TypeSafeStructuredDecisionModel(Builder builder) {
        String apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        String baseUrl = ensureNotBlank(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL), "baseUrl");
        String modelName = ensureNotBlank(getOrDefault(builder.modelName, DEFAULT_MODEL_NAME), "modelName");
        this.defaultRequestParameters = StructuredDecisionRequestParameters.builder()
                .modelName(modelName)
                .build();
        SystemOneStructuredDecisionModel.Builder delegateBuilder = SystemOneStructuredDecisionModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName);
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
    public StructuredDecisionResponse decide(StructuredDecisionRequest request) {
        return delegate.decide(request);
    }

    @Override
    public StructuredDecisionRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    /** Builder for {@link TypeSafeStructuredDecisionModel}. */
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
        public TypeSafeStructuredDecisionModel build() {
            return new TypeSafeStructuredDecisionModel(this);
        }
    }
}
