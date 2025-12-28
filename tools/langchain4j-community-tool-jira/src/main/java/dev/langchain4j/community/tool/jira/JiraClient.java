package dev.langchain4j.community.tool.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Low-level Jira Cloud REST API v3 client backed by {@link java.net.http.HttpClient}.
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
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.timeout).build();
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
        String key = requireNotBlank(issueKey, "issueKey");
        HttpRequest request = requestBuilder("/rest/api/3/issue/" + encodePathSegment(key))
                .GET()
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
        String query = requireNotBlank(jql, "jql");
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be greater than 0");
        }
        ObjectNode payload = OBJECT_MAPPER.createObjectNode().put("jql", query).put("maxResults", maxResults);
        ArrayNode fields = payload.putArray("fields");
        for (String field : SEARCH_FIELDS) {
            fields.add(field);
        }
        HttpRequest request = requestBuilder("/rest/api/3/search")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(payload), StandardCharsets.UTF_8))
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
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(payload), StandardCharsets.UTF_8))
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
        String key = requireNotBlank(issueKey, "issueKey");
        Objects.requireNonNull(body, "body must not be null");
        HttpRequest request = requestBuilder("/rest/api/3/issue/" + encodePathSegment(key) + "/comment")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(writeJson(body), StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    private HttpRequest.Builder requestBuilder(String path) {
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(baseUri.resolve(path)).timeout(timeout).header("Accept", "application/json");
        if (authentication != null) {
            authentication.apply(builder);
        }
        return builder;
    }

    private JsonNode send(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new JiraClientException("I/O error while calling Jira API", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JiraClientException("Request interrupted while calling Jira API", e);
        }
        int statusCode = response.statusCode();
        String body = response.body() == null ? "" : response.body();
        if (statusCode < 200 || statusCode >= 300) {
            throw new JiraClientException(statusCode, body);
        }
        return readJson(body);
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

    private static String requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static URI normalizeBaseUrl(String baseUrl) {
        String value = requireNotBlank(baseUrl, "baseUrl");
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
         * Builds a {@link JiraClient}.
         */
        public JiraClient build() {
            return new JiraClient(this);
        }
    }

    private static final class BearerTokenAuthentication implements Authentication {
        private final String headerValue;

        private BearerTokenAuthentication(String token) {
            String value = requireNotBlank(token, "token");
            this.headerValue = "Bearer " + value;
        }

        @Override
        public void apply(HttpRequest.Builder requestBuilder) {
            requestBuilder.header("Authorization", headerValue);
        }
    }

    private static final class BasicAuthentication implements Authentication {
        private final String headerValue;

        private BasicAuthentication(String email, String apiToken) {
            String user = requireNotBlank(email, "email");
            String token = requireNotBlank(apiToken, "apiToken");
            String raw = user + ":" + token;
            String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
            this.headerValue = "Basic " + encoded;
        }

        @Override
        public void apply(HttpRequest.Builder requestBuilder) {
            requestBuilder.header("Authorization", headerValue);
        }
    }

    /**
     * Exception thrown for non-2xx Jira responses or client errors.
     */
    public static final class JiraClientException extends RuntimeException {
        private final int statusCode;
        private final String responseBody;

        public JiraClientException(int statusCode, String responseBody) {
            super("Jira API request failed with status " + statusCode);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        public JiraClientException(String message, Throwable cause) {
            super(message, cause);
            this.statusCode = -1;
            this.responseBody = null;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getResponseBody() {
            return responseBody;
        }
    }
}
