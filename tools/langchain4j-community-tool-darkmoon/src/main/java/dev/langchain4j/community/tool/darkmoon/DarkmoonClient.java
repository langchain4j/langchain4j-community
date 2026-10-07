package dev.langchain4j.community.tool.darkmoon;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.jdk.JdkHttpClient;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Low-level client for the Dashboard API of a self-hosted
 * <a href="https://github.com/ASCIT31/Dark-Moon">Darkmoon</a> instance.
 *
 * <p>The dashboard authenticates with a bearer token obtained from
 * {@code POST /api/v1/auth/login}. The token is fetched lazily on the first
 * call, reused afterwards, and renewed once if the dashboard answers
 * {@code 401}. The dashboard API is part of Darkmoon Pro; the open source
 * engine and CLI do not expose it.
 */
final class DarkmoonClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final URI baseUri;
    private final String username;
    private final String password;
    private volatile String token;

    private DarkmoonClient(Builder builder) {
        this.baseUri = normalizeBaseUrl(builder.baseUrl);
        this.username = ensureNotBlank(builder.username, "username");
        this.password = ensureNotBlank(builder.password, "password");
        Duration timeout = Objects.requireNonNull(builder.timeout, "timeout must not be null");
        // Built explicitly rather than through the HttpClientBuilderLoader SPI: surfacing the dashboard's own
        // error sentence depends on JdkHttpClient putting the raw response body into HttpException#getMessage()
        // (see httpErrorMessage), so the client must not be swappable.
        this.httpClient = JdkHttpClient.builder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .build();
    }

    /**
     * Creates a new Darkmoon client builder.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Lists campaigns (GET /api/v1/campaigns).
     *
     * @return the response JSON: {@code {"data": [...], "total": n}}
     */
    JsonNode listCampaigns() {
        return authorized(GET, "/api/v1/campaigns", null);
    }

    /**
     * Lists the vulnerabilities of one campaign
     * (GET /api/v1/vulnerabilities?campaign_id=...).
     *
     * @param campaignId the campaign to read
     * @return the response JSON: {@code {"data": [...], "total": n, "stats": {...}}}
     */
    JsonNode listVulnerabilities(String campaignId) {
        String path = "/api/v1/vulnerabilities?campaign_id="
                + URLEncoder.encode(ensureNotBlank(campaignId, "campaignId"), StandardCharsets.UTF_8);
        return authorized(GET, path, null);
    }

    /**
     * Starts a pentest in the background (POST /api/v1/run/campaign). The
     * dashboard applies its own campaign scope safety layer and may refuse the
     * target.
     *
     * @param target the primary target URL or host
     * @param program an optional program name or rules of engagement, or {@code null}
     * @return the response JSON: {@code {"run_id": "...", "pid": n, ...}}
     */
    JsonNode startCampaign(String target, String program) {
        ObjectNode payload = OBJECT_MAPPER.createObjectNode();
        payload.put("target", ensureNotBlank(target, "target"));
        if (program != null && !program.isBlank()) {
            payload.put("program", program);
        }
        return authorized(POST, "/api/v1/run/campaign", payload);
    }

    /**
     * Reads the event log of a run (GET /api/v1/run/logs/:runId). The
     * dashboard answers {@code 404} until the run has written its first event.
     *
     * @param runId the run id returned by {@link #startCampaign}
     * @return the response JSON: {@code {"data": [events], "total": n}}
     */
    JsonNode getRunLog(String runId) {
        String path = "/api/v1/run/logs/"
                + URLEncoder.encode(ensureNotBlank(runId, "runId"), StandardCharsets.UTF_8)
                        .replace("+", "%20");
        return authorized(GET, path, null);
    }

    private JsonNode authorized(HttpMethod method, String path, ObjectNode payload) {
        if (token == null) {
            token = login();
        }
        try {
            return send(request(method, path, payload, token));
        } catch (DarkmoonClientException e) {
            if (e.statusCode() != 401) {
                throw e;
            }
            token = login();
            return send(request(method, path, payload, token));
        }
    }

    private String login() {
        ObjectNode credentials = OBJECT_MAPPER.createObjectNode();
        credentials.put("username", username);
        credentials.put("password", password);
        JsonNode response = send(request(POST, "/api/v1/auth/login", credentials, null));
        JsonNode value = response.path("token");
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new DarkmoonClientException("Darkmoon login did not return a token.");
        }
        return value.asText();
    }

    private HttpRequest request(HttpMethod method, String path, ObjectNode payload, String bearerToken) {
        HttpRequest.Builder builder = HttpRequest.builder()
                .url(baseUri.resolve(path).toString())
                .method(method)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "langchain4j-community-tool-darkmoon");
        if (bearerToken != null) {
            builder.addHeader("Authorization", "Bearer " + bearerToken);
        }
        if (payload != null) {
            builder.addHeader("Content-Type", "application/json").body(payload.toString());
        }
        return builder.build();
    }

    private JsonNode send(HttpRequest request) {
        try {
            SuccessfulHttpResponse response = httpClient.execute(request);
            String body = response.body();
            if (body == null || body.isBlank()) {
                throw new DarkmoonClientException("Darkmoon returned an empty response.");
            }
            return OBJECT_MAPPER.readTree(body);
        } catch (HttpException e) {
            throw new DarkmoonClientException(e.statusCode(), httpErrorMessage(e), e);
        } catch (JsonProcessingException e) {
            throw new DarkmoonClientException("Darkmoon returned invalid JSON.", e);
        } catch (DarkmoonClientException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DarkmoonClientException("Darkmoon request failed.", e);
        }
    }

    // The dashboard answers errors as {"detail": "..."} (FastAPI HTTPException). That sentence is surfaced
    // verbatim, since it is already written to be read by the caller (for example "Invalid credentials" or a
    // campaign scope refusal). JdkHttpClient puts the raw response body into HttpException's own message.
    private static String httpErrorMessage(HttpException e) {
        String fromBody = detailFromBody(e.getMessage());
        if (fromBody != null) {
            return "Darkmoon request failed: HTTP " + e.statusCode() + ". " + fromBody;
        }
        String guidance =
                switch (e.statusCode()) {
                    case 401 -> "Authentication failed. Check the dashboard username and password.";
                    case 403 -> "Darkmoon refused this request for this user.";
                    case 404 -> "Not found.";
                    case 422 -> "Darkmoon rejected the request as invalid.";
                    default -> "Request could not be completed.";
                };
        return "Darkmoon request failed: HTTP " + e.statusCode() + ". " + guidance;
    }

    private static String detailFromBody(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode detail = OBJECT_MAPPER.readTree(body).path("detail");
            // FastAPI HTTPException: {"detail": "a sentence"}.
            if (detail.isTextual() && !detail.asText().isBlank()) {
                return detail.asText();
            }
            // FastAPI RequestValidationError (HTTP 422): {"detail": [{"loc": [...], "msg": "...", "type": "..."}]}.
            // The items are objects, so asText() yields ""; join their "msg" fields to keep the actionable sentence.
            if (detail.isArray()) {
                List<String> messages = new ArrayList<>();
                for (JsonNode item : detail) {
                    String message = item.path("msg").asText("");
                    if (!message.isBlank()) {
                        messages.add(message);
                    }
                }
                if (!messages.isEmpty()) {
                    return String.join("; ", messages);
                }
            }
        } catch (JsonProcessingException ignored) {
            // Not JSON, or not the expected shape -- fall through to the generic message.
        }
        return null;
    }

    private static URI normalizeBaseUrl(String baseUrl) {
        String value = ensureNotBlank(baseUrl, "baseUrl");
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
     * Builder for {@link DarkmoonClient}.
     */
    static final class Builder {

        private String baseUrl;
        private String username;
        private String password;
        private Duration timeout = Duration.ofSeconds(30);

        private Builder() {}

        /**
         * Sets the dashboard API base URL, for example {@code http://localhost:8000}.
         *
         * @param baseUrl dashboard API base URL
         * @return this builder
         */
        Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets the dashboard username.
         *
         * @param username dashboard username
         * @return this builder
         */
        Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets the dashboard password.
         *
         * @param password dashboard password
         * @return this builder
         */
        Builder password(String password) {
            this.password = password;
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
         * @return configured Darkmoon client
         */
        DarkmoonClient build() {
            return new DarkmoonClient(this);
        }
    }
}
