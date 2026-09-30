package dev.langchain4j.community.web.search.brave;

import dev.langchain4j.http.client.HttpClientBuilder;
import java.time.Duration;
import java.util.Locale;

abstract class BraveBuilder<T extends BraveBuilder<T>> {

    static final String MASKED_SECRET = "********";

    String baseUrl;
    String apiKey;
    Duration timeout;
    HttpClientBuilder httpClientBuilder;
    Boolean logRequests;
    Boolean logResponses;

    @SuppressWarnings("unchecked")
    private T self() {
        return (T) this;
    }

    public T baseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        return self();
    }

    public T apiKey(String apiKey) {
        this.apiKey = apiKey;
        return self();
    }

    public T timeout(Duration timeout) {
        this.timeout = timeout;
        return self();
    }

    public T httpClientBuilder(HttpClientBuilder httpClientBuilder) {
        this.httpClientBuilder = httpClientBuilder;
        return self();
    }

    public T logRequests(Boolean logRequests) {
        this.logRequests = logRequests;
        return self();
    }

    public T logResponses(Boolean logResponses) {
        this.logResponses = logResponses;
        return self();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "(baseUrl="
                + maskUrl(baseUrl, apiKey)
                + ", apiKey="
                + maskSecret(apiKey)
                + ", timeout="
                + timeout
                + ")";
    }

    static String maskSecret(String value) {
        return value == null ? null : MASKED_SECRET;
    }

    static String maskUrl(String url, String apiKey) {
        if (url == null) {
            return null;
        }

        String maskedUrl = apiKey == null ? url : url.replace(apiKey, MASKED_SECRET);
        String[] parts = maskedUrl.split("&", -1);
        for (int i = 0; i < parts.length; i++) {
            int separator = parts[i].indexOf('=');
            if (separator >= 0 && isSensitive(parts[i].substring(0, separator))) {
                parts[i] = parts[i].substring(0, separator + 1) + MASKED_SECRET;
            }
        }
        return String.join("&", parts);
    }

    static boolean isSensitive(String name) {
        String lowerCaseName = name.toLowerCase(Locale.ROOT);
        return lowerCaseName.contains("auth")
                || lowerCaseName.contains("api-key")
                || lowerCaseName.contains("api_key")
                || lowerCaseName.contains("apikey")
                || lowerCaseName.contains("token")
                || lowerCaseName.contains("secret")
                || lowerCaseName.contains("password")
                || lowerCaseName.contains("credential")
                || lowerCaseName.contains("cookie");
    }
}
