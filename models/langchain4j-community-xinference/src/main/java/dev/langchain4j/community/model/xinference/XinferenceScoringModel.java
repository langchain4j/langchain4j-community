package dev.langchain4j.community.model.xinference;

import dev.langchain4j.community.model.xinference.client.XinferenceClient;
import dev.langchain4j.community.model.xinference.client.rerank.RerankRequest;
import dev.langchain4j.community.model.xinference.client.rerank.RerankResponse;
import dev.langchain4j.community.model.xinference.client.rerank.RerankResult;
import dev.langchain4j.community.model.xinference.client.rerank.RerankTokens;
import dev.langchain4j.community.model.xinference.spi.XinferenceScoringModelBuilderFactory;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.model.scoring.ScoringModel;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

public class XinferenceScoringModel implements ScoringModel {
    private final XinferenceClient client;
    private final String modelName;
    private final Integer topN;
    private final Boolean returnDocuments;
    private final Boolean returnLen;
    private final Integer maxRetries;

    public XinferenceScoringModel(String baseUrl,
                                  String apiKey,
                                  String modelName,
                                  Integer topN,
                                  Boolean returnDocuments,
                                  Boolean returnLen,
                                  Integer maxRetries,
                                  Duration timeout,
                                  Proxy proxy,
                                  Boolean logRequests,
                                  Boolean logResponses,
                                  Map<String, String> customHeaders) {
        timeout = getOrDefault(timeout, Duration.ofSeconds(60));

        this.client = XinferenceClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .customHeaders(customHeaders)
                .build();

        this.modelName = ensureNotBlank(modelName, "modelName");
        this.topN = topN;
        this.returnDocuments = getOrDefault(returnDocuments, false);
        this.returnLen = getOrDefault(returnLen, true);
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<List<Double>> scoreAll(final List<TextSegment> segments, final String query) {
        final List<String> documents = segments.stream().map(TextSegment::text).toList();
        final RerankRequest request = RerankRequest.builder()
                .model(modelName)
                .query(query)
                .documents(documents)
                .topN(topN)
                .returnDocuments(returnDocuments)
                .returnLen(returnLen)
                .build();
        RerankResponse response = withRetry(() -> client.rerank(request).execute(), maxRetries);
        List<Double> scores = response.getResults().stream()
                .sorted(comparingInt(RerankResult::getIndex))
                .map(RerankResult::getRelevanceScore)
                .collect(toList());
        final RerankTokens tokens = Optional.ofNullable(response.getMeta().getTokens()).orElse(RerankTokens.builder().inputTokens(0).outputTokens(0).build());
        return Response.from(scores, new TokenUsage(tokens.getInputTokens(), tokens.getOutputTokens()));
    }

    public static XinferenceScoringModelBuilder builder() {
        for (XinferenceScoringModelBuilderFactory factory : loadFactories(XinferenceScoringModelBuilderFactory.class)) {
            return factory.get();
        }
        return new XinferenceScoringModelBuilder();
    }

    public static class XinferenceScoringModelBuilder {

        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Integer topN;
        private Boolean returnDocuments;
        private Boolean returnLen;
        private Integer maxRetries;
        private Duration timeout;
        private Proxy proxy;
        private Boolean logRequests;
        private Boolean logResponses;
        private Map<String, String> customHeaders;

        public XinferenceScoringModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public XinferenceScoringModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public XinferenceScoringModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public XinferenceScoringModelBuilder topN(Integer topN) {
            this.topN = topN;
            return this;
        }

        public XinferenceScoringModelBuilder returnDocuments(Boolean returnDocuments) {
            this.returnDocuments = returnDocuments;
            return this;
        }

        public XinferenceScoringModelBuilder returnLen(Boolean returnLen) {
            this.returnLen = returnLen;
            return this;
        }

        public XinferenceScoringModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public XinferenceScoringModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public XinferenceScoringModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public XinferenceScoringModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public XinferenceScoringModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public XinferenceScoringModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        // Build method to create the object
        public XinferenceScoringModel build() {
            return new XinferenceScoringModel(baseUrl,
                    apiKey,
                    modelName,
                    topN,
                    returnDocuments,
                    returnLen,
                    maxRetries,
                    timeout,
                    proxy,
                    logRequests,
                    logResponses,
                    customHeaders
            );
        }
    }
}
