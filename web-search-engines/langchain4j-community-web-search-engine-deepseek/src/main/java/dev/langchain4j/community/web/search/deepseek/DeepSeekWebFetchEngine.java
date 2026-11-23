package dev.langchain4j.community.web.search.deepseek;

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

    private final DeepSeekClient client;

    private DeepSeekWebFetchEngine(Builder builder) {
        this.client = builder.clientBuilder.build();
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
        private final DeepSeekClient.Builder clientBuilder = DeepSeekClient.builder();

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

        public DeepSeekWebFetchEngine build() {
            return new DeepSeekWebFetchEngine(this);
        }
    }
}
