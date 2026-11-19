package dev.langchain4j.community.tool.datacircle;

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
import java.text.Normalizer;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Low-level client for the open Datacircle API at {@code https://api.datacircle.dev/v1}.
 *
 * <p>The API needs no key and no account. It allows 60 calls a minute from one address.
 */
final class DatacircleClient {

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 50;
    static final Set<String> SORTS = Set.of("score", "provider", "price");

    private static final String DEFAULT_BASE_URL = "https://api.datacircle.dev/v1";
    private static final int MAX_RESPONSE_BYTES = 20 * 1024 * 1024;
    private static final int MAX_ERROR_LENGTH = 300;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final HttpClient httpClient;
    private final String baseUrl;

    private DatacircleClient(Builder builder) {
        this.baseUrl = normalizeBaseUrl(builder.baseUrl);
        Duration timeout = Objects.requireNonNull(builder.timeout, "timeout must not be null");
        HttpClientBuilder httpClientBuilder = HttpClientBuilderLoader.loadHttpClientBuilder()
                .connectTimeout(timeout)
                .readTimeout(timeout);
        this.httpClient = httpClientBuilder.build();
    }

    /**
     * Creates a new Datacircle API client builder.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Lists every API on the sheet, in its order, with its slug, its counts and its best plan.
     *
     * @return Datacircle API response JSON, an array
     */
    JsonNode listApis() {
        return get("/apis");
    }

    /**
     * Reads one API's plans, best first by score.
     *
     * @param slug the API's slug, as {@link #listApis()} gives it
     * @param limit number of plans from 1 through 50, or {@code null} for 10
     * @return Datacircle API response JSON
     */
    JsonNode getBestPlans(String slug, Integer limit) {
        return get("/apis/" + encodePath(normalizeSlug(slug, "api")) + "/best?limit=" + normalizeLimit(limit));
    }

    /**
     * Searches the plans of every API, or of one.
     *
     * @param api the API's name or slug, or {@code null} for every API
     * @param query words that must all appear in the provider's or plan's name, or {@code null}
     * @param sort {@code score}, {@code provider} or {@code price}, or {@code null} for score
     * @param limit number of plans from 1 through 50, or {@code null} for 10
     * @param offset number of found plans to skip first, or {@code null} for 0
     * @return Datacircle API response JSON
     */
    JsonNode searchPlans(String api, String query, String sort, Integer limit, Integer offset) {
        StringBuilder path = new StringBuilder("/plans?sort=")
                .append(normalizeSort(sort))
                .append("&limit=")
                .append(normalizeLimit(limit))
                .append("&offset=")
                .append(normalizeOffset(offset));
        appendOptional(path, "api", api);
        appendOptional(path, "query", query);
        return get(path.toString());
    }

    /**
     * Reads one provider's plans and rank on each API it sells.
     *
     * @param slug the provider's slug, or its name, which is turned into its slug
     * @return Datacircle API response JSON
     */
    JsonNode getProvider(String slug) {
        return get("/providers/" + encodePath(providerSlug(slug)));
    }

    /**
     * Turns a provider's name into its slug: accents dropped, lowercase letters and digits, the rest a hyphen.
     *
     * @param name the provider's name or slug
     * @return the provider's slug
     */
    static String providerSlug(String name) {
        String slug = Normalizer.normalize(ensureNotBlank(name, "provider"), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            throw new IllegalArgumentException("provider must hold letters or digits");
        }
        return slug;
    }

    private JsonNode get(String path) {
        HttpRequest request = HttpRequest.builder()
                .url(baseUrl + path)
                .method(GET)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "langchain4j-community-tool-datacircle")
                .build();
        return send(request);
    }

    private JsonNode send(HttpRequest request) {
        try {
            SuccessfulHttpResponse response = httpClient.execute(request);
            String body = response.body();
            if (body == null || body.isBlank()) {
                throw new DatacircleClientException("The Datacircle API returned an empty response.");
            }
            if (body.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                throw new DatacircleClientException("The Datacircle API response exceeds the 20 MiB safety limit.");
            }
            return OBJECT_MAPPER.readTree(body);
        } catch (HttpException e) {
            throw new DatacircleClientException(e.statusCode(), httpErrorMessage(e.statusCode(), e.getMessage()), e);
        } catch (JsonProcessingException e) {
            throw new DatacircleClientException("The Datacircle API returned invalid JSON.", e);
        } catch (DatacircleClientException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DatacircleClientException("The Datacircle API request failed.", e);
        }
    }

    private static String httpErrorMessage(int statusCode, String body) {
        String reason = errorSentence(body);
        if (reason == null) {
            reason = switch (statusCode) {
                case 400 -> "Invalid request. Check the tool arguments.";
                case 404 -> "Not found.";
                case 429 -> "The API allows 60 calls a minute from one address. Retry in a minute.";
                default -> "Request could not be completed.";
            };
        } else if (statusCode == 429) {
            reason = reason + " The API allows 60 calls a minute from one address. Retry in a minute.";
        }
        return "Datacircle API request failed: HTTP " + statusCode + ". " + reason;
    }

    /** The API's own one-line reason, from its {@code {"error": "..."}} body, or {@code null}. */
    private static String errorSentence(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode error = OBJECT_MAPPER.readTree(body).path("error");
            if (!error.isTextual() || error.asText().isBlank()) {
                return null;
            }
            String sentence = error.asText().trim();
            if (sentence.length() > MAX_ERROR_LENGTH) {
                sentence = sentence.substring(0, MAX_ERROR_LENGTH - 3) + "...";
            }
            return sentence.endsWith(".") ? sentence : sentence + ".";
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static void appendOptional(StringBuilder path, String name, String value) {
        if (value != null && !value.isBlank()) {
            path.append('&').append(name).append('=').append(encode(value.trim()));
        }
    }

    private static int normalizeLimit(Integer limit) {
        int value = limit == null ? DEFAULT_LIMIT : limit;
        if (value < 1 || value > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        return value;
    }

    private static int normalizeOffset(Integer offset) {
        int value = offset == null ? 0 : offset;
        if (value < 0) {
            throw new IllegalArgumentException("offset must be 0 or more");
        }
        return value;
    }

    private static String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "score";
        }
        String normalized = sort.trim().toLowerCase(Locale.ROOT);
        if (!SORTS.contains(normalized)) {
            throw new IllegalArgumentException(
                    "sort must be one of " + SORTS.stream().sorted().toList());
        }
        return normalized;
    }

    private static String normalizeSlug(String slug, String name) {
        return ensureNotBlank(slug, name).trim().toLowerCase(Locale.ROOT);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String encodePath(String value) {
        return encode(value).replace("+", "%20");
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
     * Builder for {@link DatacircleClient}.
     */
    static final class Builder {

        private String baseUrl = DEFAULT_BASE_URL;
        private Duration timeout = Duration.ofSeconds(30);

        private Builder() {}

        /**
         * Sets the API base URL. Defaults to {@code https://api.datacircle.dev/v1}.
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
         * @return configured Datacircle API client
         */
        DatacircleClient build() {
            return new DatacircleClient(this);
        }
    }
}
