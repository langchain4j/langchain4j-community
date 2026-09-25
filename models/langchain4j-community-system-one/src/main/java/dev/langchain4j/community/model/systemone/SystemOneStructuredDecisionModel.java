package dev.langchain4j.community.model.systemone;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.model.structureddecision.StructuredDecisionModel;
import dev.langchain4j.model.structureddecision.StructuredDecisionRequest;
import dev.langchain4j.model.structureddecision.StructuredDecisionRequestParameters;
import dev.langchain4j.model.structureddecision.StructuredDecisionResponse;

/** A configurable client for the shared JSON System One decision endpoint. */
@Experimental
public final class SystemOneStructuredDecisionModel implements StructuredDecisionModel {

    private final SystemOneSupport support;
    private final StructuredDecisionRequestParameters defaultRequestParameters;

    private SystemOneStructuredDecisionModel(Builder builder) {
        String baseUrl = ensureNotBlank(builder.baseUrl, "baseUrl");
        HttpClientBuilder clientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);
        HttpClient httpClient = clientBuilder.build();
        this.support = new SystemOneSupport(httpClient, baseUrl, builder.apiKey);
        this.defaultRequestParameters = StructuredDecisionRequestParameters.builder()
                .modelName(builder.modelName)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public StructuredDecisionResponse decide(StructuredDecisionRequest request) {
        return support.decide(
                ensureNotNull(request, "request"), defaultRequestParameters.overrideWith(request.parameters()));
    }

    @Override
    public StructuredDecisionRequestParameters defaultRequestParameters() {
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

        public SystemOneStructuredDecisionModel build() {
            return new SystemOneStructuredDecisionModel(this);
        }
    }
}
