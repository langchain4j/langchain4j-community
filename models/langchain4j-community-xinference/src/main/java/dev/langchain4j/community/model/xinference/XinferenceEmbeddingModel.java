package dev.langchain4j.community.model.xinference;

import static dev.langchain4j.community.model.xinference.InternalXinferenceHelper.tokenUsageFrom;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.community.model.xinference.client.XinferenceClient;
import dev.langchain4j.community.model.xinference.client.embedding.EmbeddingRequest;
import dev.langchain4j.community.model.xinference.client.embedding.EmbeddingResponse;
import dev.langchain4j.community.model.xinference.spi.XinferenceEmbeddingModelBuilderFactory;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class XinferenceEmbeddingModel extends DimensionAwareEmbeddingModel {
    private final XinferenceClient client;
    private final String modelName;
    private final String user;
    private final Integer maxRetries;

    public XinferenceEmbeddingModel(
            String baseUrl,
            String apiKey,
            String modelName,
            String user,
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
        this.user = user;
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> list) {
        List<String> texts = list.stream().map(TextSegment::text).toList();
        return embedTexts(texts);
    }

    private Response<List<Embedding>> embedTexts(List<String> texts) {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .input(texts)
                .model(modelName)
                .user(user)
                .build();
        EmbeddingResponse response = withRetry(() -> client.embeddings(request).execute(), maxRetries);
        List<Embedding> embeddings = response.getData().stream()
                .map(embedding -> Embedding.from(embedding.getEmbedding()))
                .collect(toList());
        return Response.from(embeddings, tokenUsageFrom(response.getUsage()));
    }

    public static XinferenceEmbeddingModelBuilder builder() {
        for (XinferenceEmbeddingModelBuilderFactory factory :
                loadFactories(XinferenceEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new XinferenceEmbeddingModelBuilder();
    }

    public static class XinferenceEmbeddingModelBuilder {
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private String user;
        private Integer maxRetries;
        private Duration timeout;
        private Proxy proxy;
        private Boolean logRequests;
        private Boolean logResponses;
        private Map<String, String> customHeaders;

        public XinferenceEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public XinferenceEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public XinferenceEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public XinferenceEmbeddingModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        public XinferenceEmbeddingModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public XinferenceEmbeddingModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public XinferenceEmbeddingModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public XinferenceEmbeddingModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public XinferenceEmbeddingModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public XinferenceEmbeddingModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public XinferenceEmbeddingModel build() {
            return new XinferenceEmbeddingModel(
                    this.baseUrl,
                    this.apiKey,
                    this.modelName,
                    this.user,
                    this.maxRetries,
                    this.timeout,
                    this.proxy,
                    this.logRequests,
                    this.logResponses,
                    this.customHeaders);
        }
    }
}
