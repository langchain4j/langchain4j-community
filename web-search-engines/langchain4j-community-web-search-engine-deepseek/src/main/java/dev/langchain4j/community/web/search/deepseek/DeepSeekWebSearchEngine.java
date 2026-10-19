package dev.langchain4j.community.web.search.deepseek;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.UriUtils.createUriSafely;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

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

/** A {@link WebSearchEngine} backed by DeepSeek's web search API. */
public class DeepSeekWebSearchEngine implements WebSearchEngine {
    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com/v1/tools/";
    private static final int MAX_COUNT = 50;
    private final DeepSeekClient client;
    private final String originalQuestion;
    private final String localization;
    private final Boolean needContent;

    private DeepSeekWebSearchEngine(Builder builder) {
        this.client = new DeepSeekClient(
                builder.httpClientBuilder,
                getOrDefault(builder.baseUrl, DEFAULT_BASE_URL),
                ensureNotBlank(builder.apiKey, "apiKey"),
                getOrDefault(builder.timeout, Duration.ofSeconds(30)),
                builder.logRequests,
                builder.logResponses);
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
        return toWebSearchResults(client.search(toRequest(request)));
    }

    @Override
    public CompletableFuture<WebSearchResults> searchAsync(WebSearchRequest request) {
        CompletableFuture<DeepSeekSearchResponse> response = client.searchAsync(toRequest(request));
        CompletableFuture<WebSearchResults> result = response.thenApply(this::toWebSearchResults);
        propagateCancellation(result, response);
        return result;
    }

    private DeepSeekSearchRequest toRequest(WebSearchRequest request) {
        return new DeepSeekSearchRequest(
                request.searchTerms(), count(request.maxResults()), originalQuestion, localization, needContent);
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
        private String apiKey;
        private String baseUrl;
        private Duration timeout;
        private String originalQuestion;
        private String localization;
        private Boolean needContent;
        private HttpClientBuilder httpClientBuilder;
        private Boolean logRequests;
        private Boolean logResponses;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
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

        public Builder httpClientBuilder(HttpClientBuilder builder) {
            this.httpClientBuilder = builder;
            return this;
        }

        public Builder logRequests(Boolean value) {
            this.logRequests = value;
            return this;
        }

        public Builder logResponses(Boolean value) {
            this.logResponses = value;
            return this;
        }

        public DeepSeekWebSearchEngine build() {
            return new DeepSeekWebSearchEngine(this);
        }
    }
}
