package dev.langchain4j.community.model.systemone;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.DecisionRequest;
import dev.langchain4j.model.decision.DecisionRequestParameters;
import dev.langchain4j.model.decision.DecisionResponse;

/** A configurable client for the shared JSON System One decision endpoint. */
@Experimental
public final class SystemOneDecisionModel implements DecisionModel {

    private final SystemOneSupport support;
    private final DecisionRequestParameters defaultRequestParameters;

    private SystemOneDecisionModel(Builder builder) {
        String baseUrl = ensureNotBlank(builder.baseUrl, "baseUrl");
        HttpClientBuilder clientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);
        HttpClient httpClient = clientBuilder.build();
        this.support = new SystemOneSupport(httpClient, baseUrl, builder.apiKey);
        this.defaultRequestParameters =
                DecisionRequestParameters.builder().modelName(builder.modelName).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public DecisionResponse decide(DecisionRequest request) {
        return support.decide(
                ensureNotNull(request, "request"), defaultRequestParameters.overrideWith(request.parameters()));
    }

    @Override
    public DecisionRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    public static final class Builder {
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private HttpClientBuilder httpClientBuilder;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public SystemOneDecisionModel build() {
            return new SystemOneDecisionModel(this);
        }
    }
}
