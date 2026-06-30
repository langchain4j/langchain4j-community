package dev.langchain4j.community.model.twelvelabs;

import static dev.langchain4j.community.model.twelvelabs.TwelveLabsJsonUtils.fromJson;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.ensureTrailingForwardSlash;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;

class TwelveLabsClient {

    public static final String DEFAULT_BASE_URL = "https://api.twelvelabs.io/v1.3/";

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Map<String, String> defaultHeaders;
    private final Supplier<Map<String, String>> customHeadersSupplier;

    TwelveLabsClient(Builder builder) {
        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        HttpClient httpClient = httpClientBuilder
                .connectTimeout(
                        getOrDefault(getOrDefault(builder.timeout, httpClientBuilder.connectTimeout()), ofSeconds(15)))
                .readTimeout(
                        getOrDefault(getOrDefault(builder.timeout, httpClientBuilder.readTimeout()), ofSeconds(60)))
                .build();

        if (builder.logRequests != null && builder.logRequests
                || builder.logResponses != null && builder.logResponses) {
            this.httpClient =
                    new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses, builder.logger);
        } else {
            this.httpClient = httpClient;
        }

        this.baseUrl = ensureTrailingForwardSlash(ensureNotBlank(builder.baseUrl, "baseUrl"));

        Map<String, String> defaultHeaders = new HashMap<>();
        if (builder.apiKey != null) {
            defaultHeaders.put("x-api-key", builder.apiKey);
        }
        this.defaultHeaders = defaultHeaders;
        this.customHeadersSupplier = getOrDefault(builder.customHeadersSupplier, () -> Map::of);
    }

    private Map<String, String> buildRequestHeaders() {
        Map<String, String> dynamicHeaders = customHeadersSupplier.get();
        if (isNullOrEmpty(dynamicHeaders)) {
            return defaultHeaders;
        }

        Map<String, String> headers = new HashMap<>(defaultHeaders);
        headers.putAll(dynamicHeaders);
        return headers;
    }

    /**
     * Creates a text embedding for a single piece of text.
     *
     * <p>The TwelveLabs embed endpoint accepts {@code multipart/form-data} and embeds one text per request.</p>
     */
    EmbeddingResponse embedText(String modelName, String text) {

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl + "embed")
                // The JDK HttpClient does not set the multipart Content-Type (with boundary) itself, so we set it
                // explicitly using the boundary the client encodes the body with, otherwise TwelveLabs rejects the
                // request with content_type_invalid. Same approach as the Cohere integration.
                .addHeader("Content-Type", "multipart/form-data; boundary=----LangChain4j")
                .addFormDataField("model_name", modelName)
                .addFormDataField("text", text)
                .addHeader("Accept", "application/json")
                .addHeaders(buildRequestHeaders())
                .build();

        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);

        return fromJson(successfulHttpResponse.body(), EmbeddingResponse.class);
    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private Duration timeout;
        private String apiKey;
        private Boolean logRequests;
        private Boolean logResponses;
        private Logger logger;
        private Supplier<Map<String, String>> customHeadersSupplier;

        Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        /**
         * @param logger an alternate {@link Logger} to be used instead of the default one provided by Langchain4J for logging requests and responses.
         * @return {@code this}.
         */
        Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        /**
         * A supplier for custom headers to be added to each HTTP request.
         * The supplier is called before each request, allowing dynamic header values.
         *
         * @param customHeadersSupplier a supplier that provides a map of headers
         * @return builder
         */
        Builder customHeaders(Supplier<Map<String, String>> customHeadersSupplier) {
            this.customHeadersSupplier = customHeadersSupplier;
            return this;
        }

        TwelveLabsClient build() {
            return new TwelveLabsClient(this);
        }
    }
}
