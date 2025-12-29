package dev.langchain4j.community.tool.jira;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Low-level Jira Cloud REST API v3 client backed by {@link dev.langchain4j.http.client.HttpClient}.
 */
public final class JiraClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> SEARCH_FIELDS = List.of("summary", "status", "assignee", "priority");

    private final HttpClient httpClient;
    private final URI baseUri;
    private final Duration timeout;
    private final Authentication authentication;

    private JiraClient(Builder builder) {
        this.baseUri = normalizeBaseUrl(builder.baseUrl);
        this.timeout = Objects.requireNonNull(builder.timeout, "timeout must not be null");
        this.authentication = builder.authentication;
        if (builder.httpClient != null) {
            this.httpClient = builder.httpClient;
        } else {
            HttpClientBuilder httpClientBuilder =
                    HttpClientBuilderLoader.loadHttpClientBuilder().connectTimeout(timeout).readTimeout(timeout);
            this.httpClient = httpClientBuilder.build();
        }
    }

    /**
     * Creates a new builder for {@link JiraClient}.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Retrieves a Jira issue by key.
     *
     * @param issueKey issue key, e.g. "PROJ-123"
     * @return issue payload as {@link JsonNode}
     */
    public JsonNode getIssue(String issueKey) {
        String key = ensureNotBlank(issueKey, "issueKey");
        HttpRequest request = requestBuilder("/rest/api/3/issue/" + encodePathSegment(key))
                .method(GET)
                .build();
        return send(request);
    }

    /**
     * Searches Jira issues by JQL.
     *
     * @param jql JQL query
     * @param maxResults max number of results to return
     * @return search response as {@link JsonNode}
     */
    public JsonNode searchIssues(String jql, int maxResults) {
        String query = ensureNotBlank(jql, "jql");
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be greater than 0");
        }
        ObjectNode payload = OBJECT_MAPPER.createObjectNode().put("jql", query).put("maxResults", maxResults);
        ArrayNode fields = payload.putArray("fields");
        for (String field : SEARCH_FIELDS) {
            fields.add(field);
        }
        HttpRequest request = requestBuilder("/rest/api/3/search/jql")
                .addHeader("Content-Type", "application/json")
                .body(writeJson(payload))
                .method(POST)
                .build();
        return send(request);
    }

    /**
     * Creates a Jira issue with the provided payload.
     *
     * @param payload create issue payload
     * @return create issue response as {@link JsonNode}
     */
    public JsonNode createIssue(JsonNode payload) {
        Objects.requireNonNull(payload, "payload must not be null");
        HttpRequest request = requestBuilder("/rest/api/3/issue")
                .addHeader("Content-Type", "application/json")
                .body(writeJson(payload))
                .method(POST)
                .build();
        return send(request);
    }

    /**
     * Adds a comment to an issue.
     *
     * @param issueKey issue key, e.g. "PROJ-123"
     * @param body comment payload
     * @return comment response as {@link JsonNode}
     */
    public JsonNode addComment(String issueKey, JsonNode body) {
        String key = ensureNotBlank(issueKey, "issueKey");
        Objects.requireNonNull(body, "body must not be null");
        HttpRequest request = requestBuilder("/rest/api/3/issue/" + encodePathSegment(key) + "/comment")
                .addHeader("Content-Type", "application/json")
                .body(writeJson(body))
                .method(POST)
                .build();
        return send(request);
    }

    private HttpRequest.Builder requestBuilder(String path) {
        HttpRequest.Builder builder = HttpRequest.builder()
                .url(baseUri.resolve(path).toString())
                .addHeader("Accept", "application/json");
        if (authentication != null) {
            authentication.apply(builder);
        }
        return builder;
    }

    private JsonNode send(HttpRequest request) {
        try {
            SuccessfulHttpResponse response = httpClient.execute(request);
            return readJson(response.body());
        } catch (HttpException e) {
            throw new JiraClientException(e.statusCode(), e.getMessage());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new JiraClientException("I/O error while calling Jira API", e);
        }
    }

    private static JsonNode readJson(String body) {
        if (body == null || body.isBlank()) {
            return OBJECT_MAPPER.nullNode();
        }
        try {
            return OBJECT_MAPPER.readTree(body);
        } catch (IOException e) {
            throw new JiraClientException("Failed to parse JSON response from Jira", e);
        }
    }

    private static String writeJson(JsonNode payload) {
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (IOException e) {
            throw new JiraClientException("Failed to serialize JSON request for Jira", e);
        }
    }

    private static URI normalizeBaseUrl(String baseUrl) {
        String value = ensureNotBlank(baseUrl, "baseUrl");
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return URI.create(trimmed);
    }

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Jira authentication strategy applied to outgoing HTTP requests.
     */
    public interface Authentication {

        /**
         * Applies authentication headers to the request.
         *
         * @param requestBuilder request builder
         */
        void apply(HttpRequest.Builder requestBuilder);

        /**
         * Creates a bearer token authentication strategy.
         *
         * @param token bearer token value
         * @return authentication strategy
         */
        static Authentication bearerToken(String token) {
            return new BearerTokenAuthentication(token);
        }

        /**
         * Creates a basic authentication strategy.
         *
         * @param email Jira account email
         * @param apiToken Jira API token
         * @return authentication strategy
         */
        static Authentication basic(String email, String apiToken) {
            return new BasicAuthentication(email, apiToken);
        }
    }

    /**
     * Builder for {@link JiraClient}.
     */
    public static final class Builder {
        private String baseUrl;
        private Authentication authentication;
        private Duration timeout = Duration.ofSeconds(30);
        private HttpClient httpClient;

        private Builder() {}

        /**
         * Sets Jira base URL, e.g. {@code https://your-domain.atlassian.net}.
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets authentication strategy.
         */
        public Builder authentication(Authentication authentication) {
            this.authentication = authentication;
            return this;
        }

        /**
         * Sets request timeout.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
            return this;
        }

        /**
         * Sets a custom {@link HttpClient} implementation.
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
            return this;
        }

        /**
         * Builds a {@link JiraClient}.
         */
        public JiraClient build() {
            return new JiraClient(this);
        }
    }

    private static final class BearerTokenAuthentication implements Authentication {
        private final String headerValue;

        private BearerTokenAuthentication(String token) {
            String value = ensureNotBlank(token, "token");
            this.headerValue = "Bearer " + value;
        }

        @Override
        public void apply(HttpRequest.Builder requestBuilder) {
            requestBuilder.addHeader("Authorization", headerValue);
        }
    }

    private static final class BasicAuthentication implements Authentication {
        private final String headerValue;

        private BasicAuthentication(String email, String apiToken) {
            String user = ensureNotBlank(email, "email");
            String token = ensureNotBlank(apiToken, "apiToken");
            String raw = user + ":" + token;
            String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            this.headerValue = "Basic " + encoded;
        }

        @Override
        public void apply(HttpRequest.Builder requestBuilder) {
            requestBuilder.addHeader("Authorization", headerValue);
        }
    }
}
