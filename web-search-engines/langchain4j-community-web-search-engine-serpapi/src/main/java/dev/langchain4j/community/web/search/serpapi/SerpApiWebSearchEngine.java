package dev.langchain4j.community.web.search.serpapi;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.internal.CompletableFutureUtils;
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.internal.UriUtils;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An implementation of a {@link WebSearchEngine} that uses
 * <a href="https://serpapi.com">SerpApi</a> for performing web searches.
 * <p>
 * Engines: any SerpApi engine can be selected with {@link Builder#engine(String)} (default {@code google}).
 * The following engines have dedicated result mapping; any other engine falls back to the generic
 * {@code organic_results} mapping:
 * <table>
 *     <caption>Supported engines</caption>
 *     <tr><th>Engine</th><th>Results</th><th>Snippet shown to the LLM</th></tr>
 *     <tr><td>{@code google}</td><td>{@code organic_results}</td><td>date — snippet</td></tr>
 *     <tr><td>{@code google_news}</td><td>{@code news_results} (nested {@code stories} are flattened)</td>
 *         <td>source · date — snippet</td></tr>
 *     <tr><td>{@code google_scholar}</td><td>{@code organic_results}</td>
 *         <td>snippet — publication summary (cited by N)</td></tr>
 *     <tr><td>{@code youtube}</td><td>{@code video_results}</td>
 *         <td>description — channel · length · views · published date</td></tr>
 * </table>
 * <p>
 * Only the title, URL and snippet of a result reach the LLM (metadata is dropped by
 * {@code WebSearchTool} and {@code WebSearchContentRetriever}), so engine-specific details such as
 * the news source or citation count are folded into the snippet. {@code content} is always {@code null}.
 * <p>
 * Answer box and knowledge graph: for the {@code google} engine, {@link Builder#includeAnswerBox(boolean)}
 * and {@link Builder#includeKnowledgeGraph(boolean)} prepend Google's answer box and knowledge graph as
 * synthetic results, so the LLM reads Google's direct answer first. Both are off by default.
 * <p>
 * Request mapping: {@code language} → {@code hl}, {@code geoLocation} → {@code gl},
 * {@code safeSearch} → {@code safe} (google only), {@code startPage}/{@code startIndex} → {@code start}
 * (google and google_scholar; {@code startIndex} is a zero-based offset and wins over {@code startPage}).
 * {@code maxResults} is applied client-side. Engine-specific parameters can be passed through
 * {@link WebSearchRequest#additionalParams()}, which take precedence over everything else.
 * <p>
 * Credits: each live search costs one SerpApi credit, but identical searches within one hour are
 * served from SerpApi's cache for free. Use {@link Builder#noCache(boolean)} to bypass the cache.
 * <p>
 * Errors: a search with no results returns an empty list. Authentication failures, invalid requests and
 * an exhausted search quota throw non-retriable exceptions; throughput limits and server errors are
 * retried on the synchronous path. {@link #searchAsync(WebSearchRequest)} does not retry.
 */
public class SerpApiWebSearchEngine implements WebSearchEngine {

    static final String TOTAL_RESULTS_ESTIMATED_KEY = "totalResultsEstimated";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String engine;
    private final boolean includeAnswerBox;
    private final boolean includeKnowledgeGraph;
    private final SerpApiClient client;

    private SerpApiWebSearchEngine(Builder builder) {
        String apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        this.engine = ensureNotBlank(getOrDefault(builder.engine, "google"), "engine");
        this.includeAnswerBox = builder.includeAnswerBox;
        this.includeKnowledgeGraph = builder.includeKnowledgeGraph;
        this.client = new SerpApiClient(
                apiKey,
                getOrDefault(builder.baseUrl, SerpApiClient.defaultBaseUrl()),
                engine,
                builder.location,
                builder.noCache,
                builder.defaultParameters,
                SerpApiClient.buildHttpClient(
                        builder.httpClientBuilder,
                        getOrDefault(builder.timeout, Duration.ofSeconds(30)),
                        builder.logRequests,
                        builder.logResponses),
                RetryUtils.retryPolicyBuilder()
                        .maxRetries(getOrDefault(builder.maxRetries, 2))
                        .delayMillis(1000)
                        .build());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static SerpApiWebSearchEngine withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        return toWebSearchResults(
                client.search(webSearchRequest), engine, webSearchRequest, includeAnswerBox, includeKnowledgeGraph);
    }

    @Override
    public CompletableFuture<WebSearchResults> searchAsync(WebSearchRequest webSearchRequest) {
        CompletableFuture<JsonNode> future = client.searchAsync(webSearchRequest);
        CompletableFuture<WebSearchResults> result = future.thenApply(
                root -> toWebSearchResults(root, engine, webSearchRequest, includeAnswerBox, includeKnowledgeGraph));
        CompletableFutureUtils.propagateCancellation(result, future);
        return result;
    }

    static WebSearchResults toWebSearchResults(
            JsonNode root,
            String engine,
            WebSearchRequest request,
            boolean includeAnswerBox,
            boolean includeKnowledgeGraph) {
        List<WebSearchOrganicResult> results = new ArrayList<>();
        if (engine.equals("google")) {
            if (includeAnswerBox) {
                addIfNotNull(results, answerBox(root));
            }
            if (includeKnowledgeGraph) {
                addIfNotNull(results, knowledgeGraph(root));
            }
        }
        results.addAll(mapResults(root, engine));

        if (request.maxResults() != null && results.size() > request.maxResults()) {
            results = new ArrayList<>(results.subList(0, Math.max(request.maxResults(), 0)));
        }

        return WebSearchResults.from(searchMetadata(root, engine), information(root, request, results), results);
    }

    private static List<WebSearchOrganicResult> mapResults(JsonNode root, String engine) {
        switch (engine) {
            case "google_news":
                return mapNews(root.path("news_results"));
            case "google_scholar":
                return map(root.path("organic_results"), SerpApiWebSearchEngine::scholarResult);
            case "youtube":
                return map(root.path("video_results"), SerpApiWebSearchEngine::videoResult);
            default:
                return map(root.path("organic_results"), SerpApiWebSearchEngine::organicResult);
        }
    }

    private interface ItemMapper {
        WebSearchOrganicResult map(JsonNode item, int position);
    }

    private static List<WebSearchOrganicResult> map(JsonNode items, ItemMapper mapper) {
        List<WebSearchOrganicResult> results = new ArrayList<>();
        int position = 1;
        for (JsonNode item : items) {
            addIfNotNull(results, mapper.map(item, position++));
        }
        return results;
    }

    private static WebSearchOrganicResult organicResult(JsonNode item, int position) {
        String snippet = firstNonBlank(
                text(item, "snippet"),
                joined(item.path("snippet_highlighted_words"), " … "),
                text(item, "displayed_link"));
        if (snippet != null && text(item, "date") != null) {
            snippet = text(item, "date") + " — " + snippet;
        }
        Map<String, String> metadata = metadata(item, position, "organic");
        putIfNotNull(metadata, "displayed_link", text(item, "displayed_link"));
        putIfNotNull(metadata, "source", text(item, "source"));
        return result(text(item, "title"), text(item, "link"), snippet, metadata);
    }

    private static List<WebSearchOrganicResult> mapNews(JsonNode items) {
        List<WebSearchOrganicResult> results = new ArrayList<>();
        int position = 1;
        for (JsonNode item : items) {
            if (text(item, "link") == null && item.path("stories").isArray()) {
                for (JsonNode story : item.path("stories")) {
                    addIfNotNull(results, newsResult(story, position++));
                }
            } else {
                addIfNotNull(results, newsResult(item, position++));
            }
        }
        return results;
    }

    private static WebSearchOrganicResult newsResult(JsonNode item, int position) {
        String source = firstNonBlank(text(item.path("source"), "name"), text(item, "source"));
        String snippet = joinNonBlank(" · ", source, text(item, "date"));
        snippet = joinNonBlank(" — ", snippet, text(item, "snippet"));
        Map<String, String> metadata = metadata(item, position, "news");
        putIfNotNull(metadata, "source", source);
        putIfNotNull(metadata, "iso_date", text(item, "iso_date"));
        return result(text(item, "title"), text(item, "link"), snippet, metadata);
    }

    private static WebSearchOrganicResult scholarResult(JsonNode item, int position) {
        String citedBy = text(item.path("inline_links").path("cited_by"), "total");
        String snippet = joinNonBlank(" — ", text(item, "snippet"), text(item.path("publication_info"), "summary"));
        if (snippet != null && citedBy != null) {
            snippet += " (cited by " + citedBy + ")";
        }
        Map<String, String> metadata = metadata(item, position, "scholar");
        putIfNotNull(metadata, "cited_by", citedBy);
        putIfNotNull(metadata, "result_id", text(item, "result_id"));
        return result(text(item, "title"), text(item, "link"), snippet, metadata);
    }

    private static WebSearchOrganicResult videoResult(JsonNode item, int position) {
        String channel = text(item.path("channel"), "name");
        String views = item.path("views").isMissingNode() || item.path("views").isNull()
                ? null
                : item.path("views").asText() + " views";
        String details = joinNonBlank(
                " · ",
                channel,
                text(item.path("length"), "simple_text"),
                text(item, "length"),
                views,
                text(item, "published_date"));
        String snippet = joinNonBlank(" — ", text(item, "description"), details);
        Map<String, String> metadata = metadata(item, position, "video");
        putIfNotNull(metadata, "channel", channel);
        return result(text(item, "title"), text(item, "link"), snippet, metadata);
    }

    private static WebSearchOrganicResult answerBox(JsonNode root) {
        JsonNode box = root.path("answer_box");
        if (!box.isObject()) {
            return null;
        }
        String type = text(box, "type");
        String title = "Google answer: " + firstNonBlank(text(box, "title"), humanize(type), "Answer");
        String url = firstNonBlank(text(box, "link"), text(root.path("search_metadata"), "google_url"));
        Map<String, String> metadata = new HashMap<>();
        metadata.put("result_type", "answer_box");
        putIfNotNull(metadata, "answer_box_type", type);
        return result(title, url, formatAnswerBox(box), metadata);
    }

    static String formatAnswerBox(JsonNode box) {
        String type = getOrDefault(text(box, "type"), "");
        switch (type) {
            case "calculator_result":
                return joinNonBlank(" ", text(box, "problem"), text(box, "result"));
            case "weather_result": {
                String unit = text(box, "unit");
                String temperature = text(box, "temperature") == null
                        ? null
                        : text(box, "temperature") + "°"
                                + (unit == null ? "" : unit.substring(0, 1).toUpperCase());
                return joinNonBlank(", ", temperature, text(box, "weather"), text(box, "location"));
            }
            case "finance_results": {
                String price = joinNonBlank(" ", text(box, "price"), text(box, "currency"));
                String priceMovement = joinNonBlank(
                        " ",
                        text(box.path("price_movement"), "movement"),
                        text(box.path("price_movement"), "price"),
                        text(box.path("price_movement"), "percentage") == null
                                ? null
                                : "(" + text(box.path("price_movement"), "percentage") + "%)");
                return joinNonBlank(", ", joinNonBlank(": ", text(box, "title"), price), priceMovement);
            }
            default:
                return firstNonBlank(
                        text(box, "answer"),
                        text(box, "snippet"),
                        text(box, "result"),
                        joined(box.path("list"), "; "),
                        joined(box.path("snippet_highlighted_words"), " … "),
                        text(box, "description"));
        }
    }

    private static WebSearchOrganicResult knowledgeGraph(JsonNode root) {
        JsonNode graph = root.path("knowledge_graph");
        String description = text(graph, "description");
        if (!graph.isObject() || text(graph, "title") == null || description == null) {
            return null;
        }
        String type = text(graph, "type");
        String title = "Knowledge graph: " + text(graph, "title") + (type == null ? "" : " (" + type + ")");
        String sourceName = text(graph.path("source"), "name");
        String snippet = description + (sourceName == null ? "" : " Source: " + sourceName);
        String url = firstNonBlank(
                text(graph.path("source"), "link"),
                text(graph, "website"),
                text(root.path("search_metadata"), "google_url"));
        Map<String, String> metadata = new HashMap<>();
        metadata.put("result_type", "knowledge_graph");
        return result(title, url, snippet, metadata);
    }

    /**
     * Returns {@code null} (the item is skipped) when the title, URL or snippet is missing, because
     * {@code WebSearchTool} dereferences the URL and the LLM only sees title and snippet.
     */
    private static WebSearchOrganicResult result(
            String title, String url, String snippet, Map<String, String> metadata) {
        if (title == null || url == null || snippet == null) {
            return null;
        }
        URI uri = UriUtils.createUriSafely(url);
        if (uri == null) {
            return null;
        }
        return WebSearchOrganicResult.from(title, uri, snippet, null, metadata);
    }

    private static Map<String, String> metadata(JsonNode item, int position, String resultType) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("position", firstNonBlank(text(item, "position"), String.valueOf(position)));
        metadata.put("result_type", resultType);
        return metadata;
    }

    private static Map<String, Object> searchMetadata(JsonNode root, String engine) {
        JsonNode searchMetadata = root.path("search_metadata");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("engine", engine);
        putIfNotNull(metadata, "search_id", text(searchMetadata, "id"));
        putIfNotNull(metadata, "status", text(searchMetadata, "status"));
        putIfNotNull(metadata, "total_time_taken", text(searchMetadata, "total_time_taken"));
        JsonNode parameters = root.path("search_parameters");
        if (parameters.isObject()) {
            Map<String, Object> parameterMap = OBJECT_MAPPER.convertValue(parameters, Map.class);
            parameterMap.remove("api_key");
            metadata.put("search_parameters", parameterMap);
        }
        return metadata;
    }

    private static WebSearchInformationResult information(
            JsonNode root, WebSearchRequest request, List<WebSearchOrganicResult> results) {
        Map<String, Object> metadata = new HashMap<>();
        JsonNode total = root.path("search_information").path("total_results");
        long totalResults;
        if (total.isNumber()) {
            totalResults = total.asLong();
        } else {
            totalResults = results.size();
            metadata.put(TOTAL_RESULTS_ESTIMATED_KEY, true);
        }
        JsonNode current = root.path("serpapi_pagination").path("current");
        int pageNumber = current.isNumber() ? current.asInt() : getOrDefault(request.startPage(), 1);
        return WebSearchInformationResult.from(totalResults, pageNumber, metadata);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.isContainerNode()) {
            return null;
        }
        String text = value.asText().strip();
        return text.isEmpty() ? null : text;
    }

    private static String joined(JsonNode array, String separator) {
        if (!array.isArray()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        array.forEach(element -> {
            if (element.isValueNode() && !isNullOrBlank(element.asText())) {
                parts.add(element.asText().strip());
            }
        });
        return parts.isEmpty() ? null : String.join(separator, parts);
    }

    private static String firstNonBlank(String... values) {
        return Arrays.stream(values)
                .filter(value -> !isNullOrBlank(value))
                .findFirst()
                .orElse(null);
    }

    private static String joinNonBlank(String separator, String... values) {
        String joined = Stream.of(values).filter(value -> !isNullOrBlank(value)).collect(Collectors.joining(separator));
        return joined.isEmpty() ? null : joined;
    }

    private static String humanize(String type) {
        if (type == null) {
            return null;
        }
        String words = type.replace('_', ' ').replaceAll(" (result|results)$", "");
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static <T> void addIfNotNull(List<T> list, T value) {
        if (value != null) {
            list.add(value);
        }
    }

    private static <V> void putIfNotNull(Map<String, V> map, String key, V value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    @Override
    public String toString() {
        return "SerpApiWebSearchEngine{engine=" + engine + ", includeAnswerBox=" + includeAnswerBox
                + ", includeKnowledgeGraph=" + includeKnowledgeGraph + "}";
    }

    public static class Builder {

        private String apiKey;
        private String engine;
        private String location;
        private boolean includeAnswerBox;
        private boolean includeKnowledgeGraph;
        private boolean noCache;
        private Map<String, Object> defaultParameters;
        private String baseUrl;
        private Duration timeout;
        private Integer maxRetries;
        private HttpClientBuilder httpClientBuilder;
        private boolean logRequests;
        private boolean logResponses;

        /**
         * @param apiKey the SerpApi API key (required).
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /**
         * @param engine the SerpApi engine, e.g. {@code google}, {@code google_news}, {@code google_scholar},
         *               {@code youtube}, {@code bing}. Default: {@code google}.
         */
        public Builder engine(String engine) {
            this.engine = engine;
            return this;
        }

        /**
         * @param location a SerpApi location such as {@code "New Delhi, Delhi, India"} (google engine only).
         */
        public Builder location(String location) {
            this.location = location;
            return this;
        }

        /**
         * @param includeAnswerBox whether to prepend Google's answer box as a result (google engine only).
         *                         Default: {@code false}.
         */
        public Builder includeAnswerBox(boolean includeAnswerBox) {
            this.includeAnswerBox = includeAnswerBox;
            return this;
        }

        /**
         * @param includeKnowledgeGraph whether to prepend Google's knowledge graph as a result
         *                              (google engine only). Default: {@code false}.
         */
        public Builder includeKnowledgeGraph(boolean includeKnowledgeGraph) {
            this.includeKnowledgeGraph = includeKnowledgeGraph;
            return this;
        }

        /**
         * @param noCache whether to bypass SerpApi's one-hour cache (every search then costs a credit).
         *                Default: {@code false}.
         */
        public Builder noCache(boolean noCache) {
            this.noCache = noCache;
            return this;
        }

        /**
         * @param defaultParameters SerpApi parameters sent with every request, e.g.
         *                          {@code Map.of("google_domain", "google.co.in")}. Request fields and
         *                          {@link WebSearchRequest#additionalParams()} override them.
         */
        public Builder defaultParameters(Map<String, Object> defaultParameters) {
            this.defaultParameters = defaultParameters;
            return this;
        }

        /**
         * @param baseUrl the SerpApi base URL. Default: {@code https://serpapi.com}.
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * @param timeout the connect and read timeout. Default: 30 seconds.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * @param maxRetries the number of retries for transient failures (synchronous searches only). Default: 2.
         */
        public Builder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        /**
         * @param httpClientBuilder a custom HTTP client builder. Default: the one found on the classpath.
         */
        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /**
         * @param logRequests whether to log requests (the API key is masked).
         */
        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        /**
         * @param logResponses whether to log responses.
         */
        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public SerpApiWebSearchEngine build() {
            return new SerpApiWebSearchEngine(this);
        }

        @Override
        public String toString() {
            return "SerpApiWebSearchEngine.Builder{apiKey=" + (apiKey == null ? null : "****") + ", engine=" + engine
                    + ", location=" + location + ", includeAnswerBox=" + includeAnswerBox
                    + ", includeKnowledgeGraph=" + includeKnowledgeGraph + ", noCache=" + noCache
                    + ", defaultParameters=" + defaultParameters + ", baseUrl=" + baseUrl + ", timeout=" + timeout
                    + ", maxRetries=" + maxRetries + "}";
        }
    }
}
