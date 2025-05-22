package dev.langchain4j.community.model.zhipu;

import static dev.langchain4j.community.model.zhipu.InternalZhipuAiHelper.getEmbeddingUsage;
import static dev.langchain4j.community.model.zhipu.InternalZhipuAiHelper.toEmbed;
import static dev.langchain4j.community.model.zhipu.InternalZhipuAiHelper.tokenUsageFrom;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.community.model.zhipu.embedding.EmbeddingModel;
import dev.langchain4j.community.model.zhipu.embedding.EmbeddingRequest;
import dev.langchain4j.community.model.zhipu.embedding.EmbeddingResponse;
import dev.langchain4j.community.model.zhipu.shared.Usage;
import dev.langchain4j.community.model.zhipu.spi.ZhipuAiEmbeddingModelBuilderFactory;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.List;

/**
 * Represents an ZhipuAI embedding model, such as embedding-2 and embedding-3.
 */
public class ZhipuAiEmbeddingModel extends DimensionAwareEmbeddingModel {

    private final Integer maxRetries;
    private final String model;
    private final ZhipuAiClient client;
    private final Integer dimensions;

    public ZhipuAiEmbeddingModel(
            String baseUrl,
            String apiKey,
            String model,
            Integer dimensions,
            Integer maxRetries,
            Boolean logRequests,
            Boolean logResponses,
            Duration callTimeout,
            Duration connectTimeout,
            Duration readTimeout,
            Duration writeTimeout) {
        this.model = ensureNotNull(model, "model");
        this.dimensions = dimensions;
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.client = ZhipuAiClient.builder()
                .baseUrl(getOrDefault(baseUrl, "https://open.bigmodel.cn/"))
                .apiKey(apiKey)
                .callTimeout(callTimeout)
                .connectTimeout(connectTimeout)
                .writeTimeout(writeTimeout)
                .readTimeout(readTimeout)
                .logRequests(getOrDefault(logRequests, false))
                .logResponses(getOrDefault(logResponses, false))
                .build();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<EmbeddingResponse> embeddingRequests = textSegments.stream()
                .map(item -> EmbeddingRequest.builder()
                        .input(item.text())
                        .model(this.model)
                        .dimensions(this.dimensions)
                        .build())
                .map(request -> withRetry(() -> client.embedAll(request), maxRetries))
                .collect(toList());

        Usage usage = getEmbeddingUsage(embeddingRequests);

        return Response.from(toEmbed(embeddingRequests), tokenUsageFrom(usage));
    }

    public static ZhipuAiEmbeddingModelBuilder builder() {
        for (ZhipuAiEmbeddingModelBuilderFactory factories : loadFactories(ZhipuAiEmbeddingModelBuilderFactory.class)) {
            return factories.get();
        }
        return new ZhipuAiEmbeddingModelBuilder();
    }

    public static class ZhipuAiEmbeddingModelBuilder {

        private String baseUrl;
        private String apiKey;
        private String model;
        private Integer dimensions;
        private Integer maxRetries;
        private Boolean logRequests;
        private Boolean logResponses;
        private Duration callTimeout;
        private Duration connectTimeout;
        private Duration readTimeout;
        private Duration writeTimeout;

        public ZhipuAiEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public ZhipuAiEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public ZhipuAiEmbeddingModelBuilder model(EmbeddingModel model) {
            this.model = model.toString();
            return this;
        }

        public ZhipuAiEmbeddingModelBuilder model(String model) {
            this.model = model;
            return this;
        }

        public ZhipuAiEmbeddingModelBuilder dimensions(Integer dimensions) {
            this.dimensions = dimensions;
            return this;
        }

        public ZhipuAiEmbeddingModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public ZhipuAiEmbeddingModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public ZhipuAiEmbeddingModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @deprecated This method is deprecated due to {@link ZhipuAiClient} use {@link dev.langchain4j.http.client.HttpClient} as an http client.
         */
        @Deprecated(since = "1.0.0-beta4", forRemoval = true)
        public ZhipuAiEmbeddingModelBuilder callTimeout(Duration callTimeout) {
            this.callTimeout = callTimeout;
            return this;
        }

        public ZhipuAiEmbeddingModelBuilder connectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public ZhipuAiEmbeddingModelBuilder readTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        /**
         * @deprecated This method is deprecated due to {@link ZhipuAiClient} use {@link dev.langchain4j.http.client.HttpClient} as an http client.
         */
        @Deprecated(since = "1.0.0-beta4", forRemoval = true)
        public ZhipuAiEmbeddingModelBuilder writeTimeout(Duration writeTimeout) {
            this.writeTimeout = writeTimeout;
            return this;
        }

        public ZhipuAiEmbeddingModel build() {
            return new ZhipuAiEmbeddingModel(
                    this.baseUrl,
                    this.apiKey,
                    this.model,
                    this.dimensions,
                    this.maxRetries,
                    this.logRequests,
                    this.logResponses,
                    this.callTimeout,
                    this.connectTimeout,
                    this.readTimeout,
                    this.writeTimeout);
        }
    }
}
