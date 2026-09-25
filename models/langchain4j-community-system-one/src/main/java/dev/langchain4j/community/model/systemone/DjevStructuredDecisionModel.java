package dev.langchain4j.community.model.systemone;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.structureddecision.StructuredDecisionModel;
import dev.langchain4j.model.structureddecision.StructuredDecisionRequest;
import dev.langchain4j.model.structureddecision.StructuredDecisionRequestParameters;
import dev.langchain4j.model.structureddecision.StructuredDecisionResponse;
import java.util.List;
import java.util.Map;

/** System One decisions with djev span questions and image transport. */
@Experimental
public final class DjevStructuredDecisionModel implements StructuredDecisionModel {
    private final SystemOneSupport support;
    private final StructuredDecisionRequestParameters defaults;
    private final boolean multipart;

    private DjevStructuredDecisionModel(Builder builder) {
        HttpClientBuilder clientBuilder = getOrDefault(builder.httpClientBuilder,
                HttpClientBuilderLoader::loadHttpClientBuilder);
        support = new SystemOneSupport(clientBuilder.build(), ensureNotBlank(builder.baseUrl, "baseUrl"), builder.apiKey);
        defaults = StructuredDecisionRequestParameters.builder().modelName(builder.modelName).build();
        multipart = builder.multipart;
    }

    public static Builder builder() { return new Builder(); }

    @Override
    public StructuredDecisionResponse decide(StructuredDecisionRequest request) {
        ensureNotNull(request, "request");
        List<SystemOneImages.InlineImage> images = SystemOneImages.extract(request.contents(), Integer.MAX_VALUE);
        Map<String, Object> payload = support.payload(request, defaults.overrideWith(request.parameters()), true, true);
        SuccessfulHttpResponse response;
        if (multipart && !images.isEmpty()) {
            response = support.executeMultipart(payload, images);
        } else {
            if (!images.isEmpty()) payload.put("images", images.stream().map(SystemOneImages.InlineImage::dataUrl).toList());
            response = support.execute("/v1/systemone", payload);
        }
        return SystemOneSupport.decode(response, request);
    }

    @Override
    public StructuredDecisionRequestParameters defaultRequestParameters() { return defaults; }

    public static final class Builder {
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private HttpClientBuilder httpClientBuilder;
        private boolean multipart;
        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder modelName(String modelName) { this.modelName = modelName; return this; }
        public Builder multipart(boolean multipart) { this.multipart = multipart; return this; }
        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder; return this;
        }
        public DjevStructuredDecisionModel build() { return new DjevStructuredDecisionModel(this); }
    }
}
