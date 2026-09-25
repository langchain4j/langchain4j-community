package dev.langchain4j.community.model.systemone;

import static dev.langchain4j.internal.Json.fromJson;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.decision.DecisionModel;
import dev.langchain4j.model.decision.DecisionRequest;
import dev.langchain4j.model.decision.DecisionRequestParameters;
import dev.langchain4j.model.decision.DecisionResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** CLM's System One endpoint and its separate candidate ranking operation. */
@Experimental
public final class ClmDecisionModel implements DecisionModel {

    private final SystemOneSupport support;
    private final ClmRequestParameters defaults;

    private ClmDecisionModel(Builder builder) {
        HttpClientBuilder clientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);
        support =
                new SystemOneSupport(clientBuilder.build(), ensureNotBlank(builder.baseUrl, "baseUrl"), builder.apiKey);
        defaults = new ClmRequestParameters(builder.modelName, builder.temperature);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public DecisionResponse decide(DecisionRequest request) {
        ensureNotNull(request, "request");
        ClmRequestParameters parameters = defaults.overrideWith(request.parameters());
        Map<String, Object> payload = support.payload(request, parameters);
        if (parameters.temperature() != null) {
            payload.put("temperature", parameters.temperature());
        }
        SuccessfulHttpResponse httpResponse = support.execute("/v1/systemone", payload);
        DecisionResponse decoded = SystemOneSupport.decode(httpResponse, request);
        Map<String, Object> metadata = new LinkedHashMap<>(decoded.metadata());
        if (httpResponse.headers() != null) {
            httpResponse.headers().forEach((key, values) -> {
                if ("X-CLM-Latency-Ms".equalsIgnoreCase(key) && values != null && !values.isEmpty()) {
                    metadata.put("clm_latency_ms", values.get(0));
                }
            });
        }
        return DecisionResponse.builder()
                .answers(decoded.answers())
                .metadata(metadata)
                .build();
    }

    @Override
    public DecisionRequestParameters defaultRequestParameters() {
        return defaults;
    }

    @SuppressWarnings("unchecked")
    public ClmRankResult rank(String context, String question, List<String> candidates) {
        ensureNotBlank(context, "context");
        ensureNotBlank(question, "question");
        ensureNotEmpty(candidates, "candidates").forEach(candidate -> ensureNotBlank(candidate, "candidate"));
        SuccessfulHttpResponse response =
                support.execute("/v1/rank", Map.of("context", context, "question", question, "answers", candidates));
        Map<String, Object> json = fromJson(response.body(), Map.class);
        List<Map<String, Object>> ranked = (List<Map<String, Object>>) json.get("ranked");
        if (ranked == null) throw new IllegalArgumentException("CLM rank response must contain ranked");
        List<ClmRankResult.RankedCandidate> result = new ArrayList<>();
        ranked.forEach(item -> result.add(new ClmRankResult.RankedCandidate(
                ((Number) item.get("rank")).intValue(),
                (String) item.get("candidate"),
                ((Number) item.get("prob")).doubleValue())));
        return new ClmRankResult((String) json.get("model"), result);
    }

    public static final class Builder {
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Double temperature;
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

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public ClmDecisionModel build() {
            return new ClmDecisionModel(this);
        }
    }
}
