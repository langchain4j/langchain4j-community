package dev.langchain4j.community.web.search.deepseek;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.http.client.HttpClientBuilder;
import java.time.Duration;

/**
 * Fetches web pages through DeepSeek's web fetch API.
 * <br>
 * This engine is intentionally separate from {@link DeepSeekWebSearchEngine}: search and fetch are distinct
 * DeepSeek endpoints, are configured independently, and are billed separately.
 */
public class DeepSeekWebFetchEngine {

    private static final String DEFAULT_BASE_URL = "https://api.deepseek.com/v1/tools/";

    private final DeepSeekClient client;

    private DeepSeekWebFetchEngine(Builder builder) {
        this.client = new DeepSeekClient(
                builder.httpClientBuilder,
                getOrDefault(builder.baseUrl, DEFAULT_BASE_URL),
                ensureNotBlank(builder.apiKey, "apiKey"),
                getOrDefault(builder.timeout, Duration.ofSeconds(30)),
                builder.logRequests,
                builder.logResponses);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static DeepSeekWebFetchEngine withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    /**
     * Fetches a page and returns its title and Markdown content.
     *
     * @param url HTTP or HTTPS URL to fetch
     * @return the fetched page
     */
    public DeepSeekFetchResult fetch(String url) {
        DeepSeekFetchResponse response = client.fetch(ensureNotBlank(url, "url"));
        return new DeepSeekFetchResult(response.getTitle(), response.getContent());
    }

    public static class Builder {
        private String apiKey;
        private String baseUrl;
        private Duration timeout;
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

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public DeepSeekWebFetchEngine build() {
            return new DeepSeekWebFetchEngine(this);
        }
    }
}
