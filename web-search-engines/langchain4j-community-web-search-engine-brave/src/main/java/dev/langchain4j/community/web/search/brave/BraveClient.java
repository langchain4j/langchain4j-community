package dev.langchain4j.community.web.search.brave;

import static dev.langchain4j.community.web.search.brave.BraveJsonUtils.fromJson;
import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.util.HashMap;
import java.util.Map;

class BraveClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;

    BraveClient(BraveClientBuilder builder) {
        ensureNotNull(builder.timeout, "timeout");
        ensureNotBlank(builder.baseUrl, "baseUrl");
        this.apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        this.baseUrl = builder.baseUrl;

        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        HttpClient httpClient = httpClientBuilder
                .connectTimeout(builder.timeout)
                .readTimeout(builder.timeout)
                .build();

        if (Boolean.TRUE.equals(builder.logRequests) || Boolean.TRUE.equals(builder.logResponses)) {
            this.httpClient = new BraveLoggingHttpClient(httpClient, builder.logRequests, builder.logResponses, apiKey);
        } else {
            this.httpClient = httpClient;
        }
    }

    public static BraveClientBuilder builder() {
        return new BraveClientBuilder();
    }

    BraveWebSearchResponse search(BraveWebSearchRequest request) {
        ensureNotBlank(request.getQuery(), "query");

        HttpRequest.Builder httpRequestBuilder = HttpRequest.builder()
                .method(GET)
                .url(baseUrl, "res/v1/web/search")
                .addHeader("Accept", "application/json")
                .addHeader("X-Subscription-Token", apiKey);

        // Additional parameters are provided first, so that the core parameters take precedence on conflict.
        Map<String, Object> parameters = new HashMap<>();
        if (request.getAdditionalParameters() != null) {
            parameters.putAll(request.getAdditionalParameters());
        }
        putIfNotNull(parameters, "q", request.getQuery());
        putIfNotNull(parameters, "count", request.getCount());
        putIfNotNull(parameters, "offset", request.getOffset());
        putIfNotNull(parameters, "search_lang", request.getLanguage());
        putIfNotNull(parameters, "country", request.getCountry());
        putIfNotNull(parameters, "safesearch", request.getSafesearch());
        putIfNotNull(parameters, BraveWebSearchRequest.FRESHNESS, request.getFreshness());
        putIfNotNull(parameters, BraveWebSearchRequest.SPELLCHECK, request.getSpellcheck());
        putIfNotNull(parameters, BraveWebSearchRequest.EXTRA_SNIPPETS, request.getExtraSnippets());

        parameters.forEach((key, value) -> {
            if (value != null) {
                httpRequestBuilder.addQueryParam(key, String.valueOf(value));
            }
        });

        try {
            SuccessfulHttpResponse response = httpClient.execute(httpRequestBuilder.build());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw apiRequestException(response.statusCode(), response.body());
            }

            try {
                return fromJson(response.body(), BraveWebSearchResponse.class);
            } catch (RuntimeException e) {
                throw new RuntimeException(
                        "Brave Search API returned invalid JSON (status code " + response.statusCode() + "): "
                                + response.body(),
                        e);
            }
        } catch (HttpException e) {
            throw new RuntimeException(
                    "Brave Search API request failed with status code "
                            + e.statusCode()
                            + (e.statusCode() == 429 ? " (rate limit exceeded)" : "")
                            + ": "
                            + e.getMessage(),
                    e);
        }
    }

    private static RuntimeException apiRequestException(int statusCode, String body) {
        String rateLimitMessage = statusCode == 429 ? " (rate limit exceeded)" : "";
        return new RuntimeException(
                "Brave Search API request failed with status code " + statusCode + rateLimitMessage + ": " + body);
    }

    private static void putIfNotNull(Map<String, Object> parameters, String key, Object value) {
        if (value != null) {
            parameters.put(key, value);
        }
    }

    public static class BraveClientBuilder extends BraveBuilder<BraveClientBuilder> {

        BraveClientBuilder() {}

        public BraveClient build() {
            return new BraveClient(this);
        }
    }
}
