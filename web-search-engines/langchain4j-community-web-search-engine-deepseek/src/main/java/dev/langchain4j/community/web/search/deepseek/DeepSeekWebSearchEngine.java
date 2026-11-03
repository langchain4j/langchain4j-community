package dev.langchain4j.community.web.search.deepseek;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.UriUtils.createUriSafely;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A {@link WebSearchEngine} backed by DeepSeek's web search API.
 * <br>
 * The {@code ori_question}, {@code localization} and {@code need_content} parameters can be set as engine-level
 * defaults through the builder or per request through {@link WebSearchRequest#additionalParams()}. Request-level
 * values take precedence over builder defaults.
 */
public class DeepSeekWebSearchEngine implements WebSearchEngine {
    private static final int MAX_COUNT = 50;
    private final DeepSeekClient client;
    private final String originalQuestion;
    private final String localization;
    private final Boolean needContent;

    private DeepSeekWebSearchEngine(Builder builder) {
        this.client = builder.clientBuilder.build();
        this.originalQuestion = builder.originalQuestion;
        this.localization = builder.localization;
        this.needContent = builder.needContent;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static DeepSeekWebSearchEngine withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    @Override
    public WebSearchResults search(WebSearchRequest request) {
        ensureNotNull(request, "request");
        return toWebSearchResults(client.search(toRequest(request)));
    }

    @Override
    public CompletableFuture<WebSearchResults> searchAsync(WebSearchRequest request) {
        ensureNotNull(request, "request");
        CompletableFuture<DeepSeekSearchResponse> response = client.searchAsync(toRequest(request));
        CompletableFuture<WebSearchResults> result = response.thenApply(this::toWebSearchResults);
        propagateCancellation(result, response);
        return result;
    }

    private DeepSeekSearchRequest toRequest(WebSearchRequest request) {
        Map<String, Object> params = request.additionalParams();
        return new DeepSeekSearchRequest(
                request.searchTerms(),
                count(request.maxResults()),
                stringParam(params, "ori_question", originalQuestion),
                stringParam(params, "localization", localization),
                booleanParam(params, "need_content", needContent));
    }

    private static String stringParam(Map<String, Object> params, String name, String defaultValue) {
        Object value = params == null ? null : params.get(name);
        if (value == null) {
            return defaultValue;
        }
        String stringValue = String.valueOf(value);
        return stringValue.isBlank() ? defaultValue : stringValue;
    }

    private static Boolean booleanParam(Map<String, Object> params, String name, Boolean defaultValue) {
        Object value = params == null ? null : params.get(name);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Boolean.valueOf(stringValue);
        }
        return defaultValue;
    }

    private WebSearchResults toWebSearchResults(DeepSeekSearchResponse response) {
        List<WebSearchOrganicResult> results = new ArrayList<>();
        if (response != null && response.getResults() != null) {
            for (DeepSeekSearchResponse.Result result : response.getResults()) {
                URI url = createUriSafely(result.getUrl());
                if (url == null
                        || result.getTitle() == null
                        || result.getTitle().isBlank()) {
                    continue;
                }
                // DeepSeek returns an empty string when content was not requested; the core contract
                // represents absent content as null.
                String content = isNullOrBlank(result.getContent()) ? null : result.getContent();
                results.add(WebSearchOrganicResult.from(
                        result.getTitle(), url, result.getSnippet(), content, metadata(result)));
            }
        }
        return WebSearchResults.from(WebSearchInformationResult.from((long) results.size()), results);
    }

    private static Integer count(Integer count) {
        return count == null ? null : Math.min(Math.max(count, 1), MAX_COUNT);
    }

    private static Map<String, String> metadata(DeepSeekSearchResponse.Result result) {
        Map<String, String> metadata = new HashMap<>();
        if (isNotNullOrBlank(result.getSummary())) metadata.put("summary", result.getSummary());
        if (isNotNullOrBlank(result.getSiteName())) metadata.put("site_name", result.getSiteName());
        if (isNotNullOrBlank(result.getSiteIcon())) metadata.put("site_icon", result.getSiteIcon());
        if (isNotNullOrBlank(result.getDate())) metadata.put("date", result.getDate());
        if (result.getRerankScore() != null)
            metadata.put("rerank_score", result.getRerankScore().toString());
        if (result.getAuthority() != null)
            metadata.put("authority", result.getAuthority().toString());
        return metadata;
    }

    public static class Builder {
        private final DeepSeekClient.Builder clientBuilder = DeepSeekClient.builder();
        private String originalQuestion;
        private String localization;
        private Boolean needContent;

        public Builder apiKey(String apiKey) {
            clientBuilder.apiKey(apiKey);
            return this;
        }

        /**
         * Sets the DeepSeek API host, for example {@code https://api.deepseek.com}.
         * <br>
         * The {@code /v1/tools/} path is appended internally, so the value must not include it.
         */
        public Builder baseUrl(String baseUrl) {
            clientBuilder.baseUrl(baseUrl);
            return this;
        }

        public Builder timeout(Duration timeout) {
            clientBuilder.timeout(timeout);
            return this;
        }

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            clientBuilder.httpClientBuilder(httpClientBuilder);
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            clientBuilder.logRequests(logRequests);
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            clientBuilder.logResponses(logResponses);
            return this;
        }

        public Builder originalQuestion(String originalQuestion) {
            this.originalQuestion = originalQuestion;
            return this;
        }

        public Builder localization(String localization) {
            this.localization = localization;
            return this;
        }

        public Builder needContent(Boolean needContent) {
            this.needContent = needContent;
            return this;
        }

        public DeepSeekWebSearchEngine build() {
            return new DeepSeekWebSearchEngine(this);
        }
    }
}
