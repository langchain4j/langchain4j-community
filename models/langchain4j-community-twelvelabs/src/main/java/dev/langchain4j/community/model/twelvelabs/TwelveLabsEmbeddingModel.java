package dev.langchain4j.community.model.twelvelabs;

import static dev.langchain4j.community.model.twelvelabs.TwelveLabsClient.DEFAULT_BASE_URL;
import static dev.langchain4j.internal.RetryUtils.withRetryMappingExceptions;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;

/**
 * An implementation of an {@link EmbeddingModel} that uses the
 * <a href="https://docs.twelvelabs.io/v1.3/api-reference/text-image-embeddings/create-text-embeddings">TwelveLabs
 * text embedding API</a> (Marengo).
 *
 * <p>Marengo is a multimodal model: it produces embeddings for text, image, audio and video that live in the same
 * vector space, so text embeddings created here can be used to search a video corpus indexed with the same model.
 * This class exposes the text embedding capability through the standard LangChain4j {@link EmbeddingModel} contract.</p>
 *
 * <p>The TwelveLabs embed endpoint embeds a single piece of text per request; {@link #embedAll(List)} therefore
 * issues one request per {@link TextSegment}.</p>
 */
public class TwelveLabsEmbeddingModel extends DimensionAwareEmbeddingModel {

    private final TwelveLabsClient client;
    private final Integer maxRetries;
    private final String modelName;

    public TwelveLabsEmbeddingModel(Builder builder) {
        this.maxRetries = getOrDefault(builder.maxRetries, 2);
        this.modelName = ensureNotBlank(builder.modelName, "modelName");

        this.client = TwelveLabsClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(builder.apiKey, "apiKey"))
                .timeout(getOrDefault(builder.timeout, ofSeconds(60)))
                .logRequests(getOrDefault(builder.logRequests, false))
                .logResponses(getOrDefault(builder.logResponses, false))
                .logger(builder.logger)
                .customHeaders(builder.customHeadersSupplier)
                .build();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = new ArrayList<>();

        for (TextSegment textSegment : textSegments) {
            EmbeddingResponse response =
                    withRetryMappingExceptions(() -> client.embedText(modelName, textSegment.text()), maxRetries);
            embeddings.add(Embedding.from(extractVector(response)));
        }

        return Response.from(embeddings);
    }

    @Override
    public String modelName() {
        return this.modelName;
    }

    @Override
    protected Integer knownDimension() {
        return TwelveLabsEmbeddingModelName.knownDimension(modelName);
    }

    private static List<Float> extractVector(EmbeddingResponse response) {
        if (response == null
                || response.getTextEmbedding() == null
                || response.getTextEmbedding().getSegments() == null
                || response.getTextEmbedding().getSegments().isEmpty()) {
            throw new IllegalStateException("TwelveLabs returned no text embedding");
        }
        return response.getTextEmbedding().getSegments().get(0).getFloat();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private Supplier<Map<String, String>> customHeadersSupplier;
        private String baseUrl;
        private Duration timeout;
        private Integer maxRetries;
        private String apiKey;
        private String modelName;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;

        /**
         * Sets a custom HTTP client builder, allowing fine-grained control over the HTTP client
         * configuration such as timeouts and proxy settings.
         *
         * @param httpClientBuilder the HTTP client builder
         * @return {@code this}
         */
        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * Sets custom HTTP headers.
         */
        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeadersSupplier = () -> customHeaders;
            return this;
        }

        /**
         * Sets a supplier for custom HTTP headers.
         * The supplier is called before each request, allowing dynamic header values.
         */
        public Builder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        /**
         * Sets the base URL of the TwelveLabs API.
         * Defaults to {@code "https://api.twelvelabs.io/v1.3/"}.
         *
         * @param baseUrl the base URL
         * @return {@code this}
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the HTTP request timeout. Defaults to 60 seconds.
         *
         * @param timeout the request timeout
         * @return {@code this}
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Sets the maximum number of retries on transient errors. Defaults to {@code 2}.
         *
         * @param maxRetries the maximum number of retries
         * @return {@code this}
         */
        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * Sets the TwelveLabs API key used to authenticate requests.
         * See the <a href="https://playground.twelvelabs.io/dashboard/api-key">TwelveLabs dashboard</a> to obtain a key.
         *
         * @param apiKey the TwelveLabs API key
         * @return {@code this}
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Name of the model.
         *
         * @param modelName Name of the model.
         * @see TwelveLabsEmbeddingModelName
         */
        public Builder modelName(TwelveLabsEmbeddingModelName modelName) {
            this.modelName = modelName.toString();
            return this;
        }

        /**
         * Name of the model.
         *
         * @param modelName Name of the model.
         * @see TwelveLabsEmbeddingModelName
         */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /**
         * Enables debug logging of request bodies sent to the TwelveLabs API.
         *
         * @param logRequests {@code true} to enable request logging
         * @return {@code this}
         */
        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * Enables debug logging of response bodies received from the TwelveLabs API.
         *
         * @param logResponses {@code true} to enable response logging
         * @return {@code this}
         */
        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public TwelveLabsEmbeddingModel build() {
            return new TwelveLabsEmbeddingModel(this);
        }
    }
}
