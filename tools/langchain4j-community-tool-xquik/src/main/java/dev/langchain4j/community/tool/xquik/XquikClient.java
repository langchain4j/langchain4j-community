package dev.langchain4j.community.tool.xquik;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Low-level client for focused, read-only Xquik X/Twitter research workflows.
 */
final class XquikClient {

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 100;

    private static final String DEFAULT_BASE_URL = "https://xquik.com";
    private static final String API_CONTRACT = "2026-04-29";
    private static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final URI baseUri;
    private final String apiKey;

    private XquikClient(Builder builder) {
        this.baseUri = normalizeBaseUrl(builder.baseUrl);
        this.apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        Duration timeout = Objects.requireNonNull(builder.timeout, "timeout must not be null");
        HttpClientBuilder httpClientBuilder = HttpClientBuilderLoader.loadHttpClientBuilder()
                .connectTimeout(timeout)
                .readTimeout(timeout);
        this.httpClient = httpClientBuilder.build();
    }

    /**
     * Creates a new Xquik client builder.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Searches public X/Twitter posts.
     *
     * @param query search query
     * @param queryType {@code Latest}, {@code Top}, or {@code null} for {@code Latest}
     * @param limit result limit from 1 through 100, or {@code null} for 10
     * @param cursor pagination cursor, or {@code null} for the first page
     * @return Xquik response JSON
     */
    JsonNode searchTweets(String query, String queryType, Integer limit, String cursor) {
        String path = "/api/v1/x/tweets/search?q=" + encodeQuery(ensureNotBlank(query, "query"))
                + "&queryType=" + normalizeQueryType(queryType)
                + "&limit=" + normalizeLimit(limit);
        return get(appendCursor(path, cursor));
    }

    /**
     * Reads recent public posts from an X/Twitter profile.
     *
     * @param userId username or X user ID
     * @param includeReplies whether replies should be included
     * @param limit result limit from 1 through 100, or {@code null} for 10
     * @param cursor pagination cursor, or {@code null} for the first page
     * @return Xquik response JSON
     */
    JsonNode getUserTweets(String userId, Boolean includeReplies, Integer limit, String cursor) {
        String path = "/api/v1/x/users/" + encodePathSegment(ensureNotBlank(userId, "userId"))
                + "/tweets?includeReplies=" + Boolean.TRUE.equals(includeReplies)
                + "&limit=" + normalizeLimit(limit);
        return get(appendCursor(path, cursor));
    }

    /**
     * Checks the follower relationship between two public X/Twitter profiles.
     *
     * @param source source username or profile URL
     * @param target target username or profile URL
     * @return Xquik response JSON
     */
    JsonNode checkFollow(String source, String target) {
        String path = "/api/v1/x/followers/check?source=" + encodeQuery(ensureNotBlank(source, "source")) + "&target="
                + encodeQuery(ensureNotBlank(target, "target"));
        return get(path);
    }

    /**
     * Resolves downloadable media from one public X/Twitter post.
     *
     * @param tweetInput tweet ID or status URL
     * @return Xquik response JSON
     */
    JsonNode downloadMedia(String tweetInput) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("tweetInput", ensureNotBlank(tweetInput, "tweetInput"));
        HttpRequest request = requestBuilder("/api/v1/x/media/download")
                .method(POST)
                .addHeader("Content-Type", "application/json")
                .body(payload.toString())
                .build();
        return send(request);
    }

    private JsonNode get(String path) {
        return send(requestBuilder(path).method(GET).build());
    }

    private HttpRequest.Builder requestBuilder(String path) {
        return HttpRequest.builder()
                .url(baseUri.resolve(path).toString())
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "langchain4j-community-tool-xquik")
                .addHeader("x-api-key", apiKey)
                .addHeader("xquik-api-contract", API_CONTRACT);
    }

    private JsonNode send(HttpRequest request) {
        try {
            SuccessfulHttpResponse response = httpClient.execute(request);
            String body = response.body();
            if (body == null || body.isBlank()) {
                throw new XquikClientException("Xquik returned an empty response.");
            }
            if (body.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                throw new XquikClientException("Xquik response exceeds the 5 MiB safety limit.");
            }
            return OBJECT_MAPPER.readTree(body);
        } catch (HttpException e) {
            throw new XquikClientException(e.statusCode(), httpErrorMessage(e.statusCode()), e);
        } catch (JsonProcessingException e) {
            throw new XquikClientException("Xquik returned invalid JSON.", e);
        } catch (XquikClientException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new XquikClientException("Xquik request failed.", e);
        }
    }

    private static String appendCursor(String path, String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return path;
        }
        return path + "&cursor=" + encodeQuery(cursor);
    }

    private static String httpErrorMessage(int statusCode) {
        String guidance =
                switch (statusCode) {
                    case 400 -> "Invalid request. Check the tool arguments.";
                    case 401 -> "Authentication failed. Check the API key.";
                    case 402 -> "Credits required. Top up or subscribe first.";
                    case 404 -> "Requested X/Twitter resource not found.";
                    case 429 -> "Rate limit exceeded. Retry later.";
                    default -> "Request could not be completed.";
                };
        return "Xquik request failed: HTTP " + statusCode + ". " + guidance;
    }

    private static int normalizeLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        if (value < 1 || value > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        return value;
    }

    private static String normalizeQueryType(String queryType) {
        if (queryType == null || queryType.isBlank()) {
            return "Latest";
        }
        String normalized = queryType.toLowerCase(Locale.ROOT);
        if ("latest".equals(normalized)) {
            return "Latest";
        }
        if ("top".equals(normalized)) {
            return "Top";
        }
        throw new IllegalArgumentException("queryType must be Latest or Top");
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodePathSegment(String value) {
        return encodeQuery(value).replace("+", "%20");
    }

    private static URI normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl;
        URI uri = URI.create(value.endsWith("/") ? value : value + "/");
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("baseUrl must use HTTP or HTTPS");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("baseUrl must include a host");
        }
        return uri;
    }

    /**
     * Builder for {@link XquikClient}.
     */
    static final class Builder {

        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private Duration timeout = Duration.ofSeconds(30);

        private Builder() {}

        /**
         * Sets the Xquik API key.
         *
         * @param apiKey Xquik API key
         * @return this builder
         */
        Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the API base URL. Defaults to {@code https://xquik.com}.
         *
         * @param baseUrl API base URL
         * @return this builder
         */
        Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets connection and read timeouts.
         *
         * @param timeout connection and read timeout
         * @return this builder
         */
        Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
            return this;
        }

        /**
         * Builds the client.
         *
         * @return configured Xquik client
         */
        XquikClient build() {
            return new XquikClient(this);
        }
    }
}
