package dev.langchain4j.community.tool.livetennis;

import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Low-level client for the read-only endpoints of the Live Tennis API.
 *
 * <p>The API key is supplied through the builder and sent as the {@code X-API-Key} request header.
 * It is never written to a log or included in an exception message by this class.
 */
final class LiveTennisClient {

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 200;

    /** Ranking systems that the rank-ordered listing mode of {@code GET /rankings} can serve. */
    static final Set<String> RANKING_SYSTEMS =
            Set.of("atp", "wta", "atp_doubles", "wta_doubles", "itf_jt", "itf_mt", "itf_wt");

    private static final String DEFAULT_BASE_URL = "https://api.livetennisapi.com/api/public/v1";
    private static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024;
    private static final int MIN_PLAYER_NAME_LENGTH = 3;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> TOURS = Set.of("atp", "wta", "challenger", "itf", "juniors");
    private static final Set<String> DRAWS = Set.of("singles", "doubles");
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;

    private LiveTennisClient(Builder builder) {
        this.baseUrl = normalizeBaseUrl(builder.baseUrl);
        this.apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        Duration timeout = Objects.requireNonNull(builder.timeout, "timeout must not be null");
        HttpClientBuilder httpClientBuilder = HttpClientBuilderLoader.loadHttpClientBuilder()
                .connectTimeout(timeout)
                .readTimeout(timeout);
        this.httpClient = httpClientBuilder.build();
    }

    /**
     * Creates a new Live Tennis API client builder.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Lists the matches currently in play.
     *
     * @param tour tour filter, or {@code null} for all tours
     * @param draw {@code singles}, {@code doubles}, or {@code null} for both
     * @param limit result limit from 1 through 200, or {@code null} for 10
     * @return Live Tennis API response JSON
     */
    JsonNode listLiveMatches(String tour, String draw, Integer limit) {
        StringBuilder path = new StringBuilder("/matches?status=live&limit=").append(normalizeLimit(limit));
        appendOptional(path, "tour", normalizeEnum(tour, TOURS, "tour"));
        appendOptional(path, "draw", normalizeEnum(draw, DRAWS, "draw"));
        return get(path.toString());
    }

    /**
     * Reads the full detail of one match.
     *
     * @param matchId the match id
     * @return Live Tennis API response JSON
     */
    JsonNode getMatch(long matchId) {
        return get("/matches/" + normalizeMatchId(matchId));
    }

    /**
     * Lists upcoming scheduled fixtures, earliest first.
     *
     * @param tour tour filter, or {@code null} for all tours
     * @param draw {@code singles}, {@code doubles}, or {@code null} for both
     * @param limit result limit from 1 through 200, or {@code null} for 10
     * @return Live Tennis API response JSON
     */
    JsonNode listFixtures(String tour, String draw, Integer limit) {
        StringBuilder path = new StringBuilder("/fixtures?limit=").append(normalizeLimit(limit));
        appendOptional(path, "tour", normalizeEnum(tour, TOURS, "tour"));
        appendOptional(path, "draw", normalizeEnum(draw, DRAWS, "draw"));
        return get(path.toString());
    }

    /**
     * Reads one published ranking table in rank order.
     *
     * @param system ranking system, one of {@link #RANKING_SYSTEMS}
     * @param asOf {@code YYYY-MM-DD} publication date, or {@code null} for the latest table
     * @param limit result limit from 1 through 200, or {@code null} for 10
     * @return Live Tennis API response JSON
     */
    JsonNode listRankings(String system, String asOf, Integer limit) {
        StringBuilder path = new StringBuilder("/rankings?system=")
                .append(normalizeEnum(ensureNotBlank(system, "system"), RANKING_SYSTEMS, "system"))
                .append("&limit=")
                .append(normalizeLimit(limit));
        appendOptional(path, "as_of", normalizeDate(asOf));
        return get(path.toString());
    }

    /**
     * Reads the head-to-head record between two players.
     *
     * @param player1 first player name, at least three characters
     * @param player2 second player name, at least three characters
     * @return Live Tennis API response JSON
     */
    JsonNode getHeadToHead(String player1, String player2) {
        String path = "/h2h?p1=" + encode(normalizePlayerName(player1, "player1")) + "&p2="
                + encode(normalizePlayerName(player2, "player2"));
        return get(path);
    }

    private JsonNode get(String path) {
        HttpRequest request = HttpRequest.builder()
                .url(baseUrl + path)
                .method(GET)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "langchain4j-community-tool-live-tennis")
                .addHeader("X-API-Key", apiKey)
                .build();
        return send(request);
    }

    private JsonNode send(HttpRequest request) {
        try {
            SuccessfulHttpResponse response = httpClient.execute(request);
            String body = response.body();
            if (body == null || body.isBlank()) {
                throw new LiveTennisClientException("The Live Tennis API returned an empty response.");
            }
            if (body.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                throw new LiveTennisClientException("The Live Tennis API response exceeds the 5 MiB safety limit.");
            }
            return OBJECT_MAPPER.readTree(body);
        } catch (HttpException e) {
            throw new LiveTennisClientException(e.statusCode(), httpErrorMessage(e.statusCode()), e);
        } catch (JsonProcessingException e) {
            throw new LiveTennisClientException("The Live Tennis API returned invalid JSON.", e);
        } catch (LiveTennisClientException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new LiveTennisClientException("The Live Tennis API request failed.", e);
        }
    }

    private static void appendOptional(StringBuilder path, String name, String value) {
        if (value != null) {
            path.append('&').append(name).append('=').append(encode(value));
        }
    }

    private static String httpErrorMessage(int statusCode) {
        String guidance =
                switch (statusCode) {
                    case 400 -> "Invalid request. Check the tool arguments.";
                    case 401 -> "Authentication failed. Check the API key.";
                    case 403 -> "The API key's plan does not include this endpoint.";
                    case 404 -> "Requested tennis resource not found.";
                    case 410 -> "That match id was merged into another match record.";
                    case 429 -> "Rate limit or daily quota exceeded. Retry later.";
                    default -> "Request could not be completed.";
                };
        return "Live Tennis API request failed: HTTP " + statusCode + ". " + guidance;
    }

    private static int normalizeLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        if (value < 1 || value > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        return value;
    }

    private static long normalizeMatchId(long matchId) {
        if (matchId < 1) {
            throw new IllegalArgumentException("matchId must be a positive match id");
        }
        return matchId;
    }

    private static String normalizeEnum(String value, Set<String> allowed, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(
                    name + " must be one of " + allowed.stream().sorted().toList());
        }
        return normalized;
    }

    private static String normalizeDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        String normalized = date.trim();
        if (!DATE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("as_of must be a YYYY-MM-DD date");
        }
        return normalized;
    }

    private static String normalizePlayerName(String name, String parameter) {
        String normalized = ensureNotBlank(name, parameter).trim();
        if (normalized.length() < MIN_PLAYER_NAME_LENGTH) {
            throw new IllegalArgumentException(parameter + " must be at least " + MIN_PLAYER_NAME_LENGTH
                    + " characters, so it can identify one player");
        }
        return normalized;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String normalizeBaseUrl(String baseUrl) {
        String value = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("baseUrl is not a valid URL", e);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("baseUrl must use HTTP or HTTPS");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("baseUrl must include a host");
        }
        return value;
    }

    /**
     * Builder for {@link LiveTennisClient}.
     */
    static final class Builder {

        private String apiKey;
        private String baseUrl = DEFAULT_BASE_URL;
        private Duration timeout = Duration.ofSeconds(30);

        private Builder() {}

        /**
         * Sets the Live Tennis API key.
         *
         * @param apiKey Live Tennis API key
         * @return this builder
         */
        Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * Sets the API base URL. Defaults to {@code https://api.livetennisapi.com/api/public/v1}.
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
         * @return configured Live Tennis API client
         */
        LiveTennisClient build() {
            return new LiveTennisClient(this);
        }
    }
}
