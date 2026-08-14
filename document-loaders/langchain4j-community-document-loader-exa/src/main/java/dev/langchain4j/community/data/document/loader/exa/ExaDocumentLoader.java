package dev.langchain4j.community.data.document.loader.exa;

import static dev.langchain4j.internal.Utils.firstNotNull;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link dev.langchain4j.data.document.Document} loader that integrates with
 * the <a href="https://exa.ai" target="_blank">Exa</a> search API to retrieve
 * relevant web documents based on a query.
 *
 * <p>
 * This loader sends a search request to the Exa API and converts the response
 * into a list of {@link dev.langchain4j.data.document.Document} objects. Each
 * document contains extracted text content along with structured
 * {@link dev.langchain4j.data.document.Metadata} such as title, URL, author,
 * publication date, and relevance score.
 * </p>
 *
 * <h2>Key Features</h2>
 * <ul>
 * <li>Supports different search types via {@link ExaSearchType}</li>
 * <li>Optionally includes full text content from search results</li>
 * <li>Gracefully falls back to highlights or title if full text is
 * unavailable</li>
 * <li>Provides rich metadata for downstream processing</li>
 * </ul>
 *
 * <h2>Exa API Reference</h2>
 * <ul>
 * <li>Search API:
 * <a href="https://docs.exa.ai/reference/search" target="_blank">
 * https://docs.exa.ai/reference/search </a></li>
 * <li>Official Website: <a href="https://exa.ai" target="_blank">
 * https://exa.ai </a></li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * ExaDocumentLoader loader = ExaDocumentLoader.builder().apiKey("your-api-key").numResults(5).build();
 *
 * List<Document> documents = loader.load("latest AI research");
 * }</pre>
 *
 * <h2>Error Handling</h2>
 * <p>
 * All API and parsing errors are wrapped in {@link ExaDocumentLoaderException}
 * to provide a consistent exception model.
 * </p>
 *
 * @see ExaSearchType
 * @see dev.langchain4j.data.document.Document
 * @see dev.langchain4j.data.document.Metadata
 */
public class ExaDocumentLoader {

    public static final String SEARCH_URL = "https://api.exa.ai/search";

    public static final String METADATA_TITLE = "title";
    public static final String METADATA_EXA_ID = "exa_id";
    public static final String METADATA_SCORE = "score";
    public static final String METADATA_PUBLISHED_DATE = "published_date";
    public static final String METADATA_AUTHOR = "author";

    // Exa API response field names
    private static final String FIELD_RESULTS = "results";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_URL = "url";
    private static final String FIELD_ID = "id";
    private static final String FIELD_PUBLISHED_DATE = "publishedDate";
    private static final String FIELD_AUTHOR = "author";
    private static final String FIELD_SCORE = "score";
    private static final String FIELD_TEXT = "text";
    private static final String FIELD_HIGHLIGHTS = "highlights";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final String apiKey;
    private final int numResults;
    private final ExaSearchType searchType;
    private final boolean includeText;

    private ExaDocumentLoader(Builder builder) {
        this.apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        this.numResults = builder.numResults;
        this.searchType = firstNotNull("searchType", builder.searchType, ExaSearchType.AUTO);
        this.includeText = builder.includeText;

        this.httpClient = firstNotNull("httpClient", builder.httpClient, new JdkHttpClientBuilder().build());

        this.objectMapper = firstNotNull("objectMapper", builder.objectMapper, new ObjectMapper());
    }

    /**
     * Executes a search query against the Exa API and returns the results as a list
     * of {@link Document} objects.
     *
     * <p>
     * This method validates the input query, performs an HTTP request to the Exa
     * search endpoint, and transforms each result into a {@link Document} with
     * associated {@link dev.langchain4j.data.document.Metadata}.
     * </p>
     *
     * <p>
     * The document content is derived in the following order:</p>
     * <ul>
     * <li>Full text (if available and enabled)</li>
     * <li>Highlights returned by the API</li>
     * <li>Title as a fallback</li>
     * </ul>
     *
     *
     * <p>
     * If the API response contains no results, an empty list is returned.
     * </p>
     *
     * @param query the search query to execute; must not be null, empty, or blank
     * @return a list of {@link Document} objects representing search results; never
     *         {@code null}
     * @throws IllegalArgumentException   if {@code query} is null, empty, or blank
     * @throws ExaDocumentLoaderException if the Exa API request fails, returns an
     *                                    error response, or the response cannot be
     *                                    parsed
     */
    public List<Document> loadDocuments(String query) {
        ensureNotBlank(query, "query");

        JsonNode response = search(query);

        List<Document> documents = new ArrayList<>();

        JsonNode results = response.path(FIELD_RESULTS);

        if (!results.isArray() || results.isEmpty()) {
            return documents;
        }

        for (JsonNode result : results) {
            documents.add(toDocument(result));
        }

        return documents;
    }

    /**
     * Sends a search request to the Exa API for the given query and returns the
     * parsed JSON response.
     *
     * <p>
     * This method builds an HTTP POST request with the configured API key, search
     * type, result count, and text inclusion settings. The response body is parsed
     * into a Jackson {@link JsonNode} for further processing.
     * </p>
     *
     * <p>
     * Any HTTP, parsing, or runtime errors are wrapped in
     * {@link ExaDocumentLoaderException}.
     * </p>
     *
     * @param query the search query to execute
     * @return the parsed JSON response from the Exa API
     * @throws ExaDocumentLoaderException if the API request fails, returns an HTTP
     *                                    error, or the response cannot be parsed
     */
    private JsonNode search(String query) {
        HttpRequest request = HttpRequest.builder()
                .url(SEARCH_URL)
                .method(HttpMethod.POST)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("x-api-key", apiKey)
                .body(buildRequestBody(query))
                .build();

        try {
            SuccessfulHttpResponse response = httpClient.execute(request);
            return objectMapper.readTree(response.body());

        } catch (HttpException e) {
            throw new ExaDocumentLoaderException(
                    "Exa API request failed with status code " + e.statusCode() + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new ExaDocumentLoaderException("Failed to parse Exa API response", e);
        } catch (RuntimeException e) {
            throw new ExaDocumentLoaderException("Failed to call Exa API", e);
        }
    }

    /**
     * Builds the JSON request body for the Exa search API.
     *
     * <p>
     * The generated payload includes the search query, the maximum number of
     * results to return, and the configured search type. When text inclusion is
     * enabled, the request also includes content settings to fetch text up to a
     * maximum character limit.
     * </p>
     *
     * <p>
     * The resulting JSON string is used as the body of the HTTP POST request sent
     * to the Exa search endpoint.
     * </p>
     *
     * @param query the search query to include in the request body
     * @return the serialized JSON request body as a string
     * @throws ExaDocumentLoaderException if the request body cannot be created or
     *                                    serialized to JSON
     */
    private String buildRequestBody(String query) {
        try {
            ObjectNode requestBody = objectMapper
                    .createObjectNode()
                    .put("query", query)
                    .put("numResults", numResults)
                    .put("type", searchType.value());

            if (includeText) {
                requestBody.putObject("contents").putObject(FIELD_TEXT).put("maxCharacters", 10000);
            }

            return objectMapper.writeValueAsString(requestBody);

        } catch (Exception e) {
            throw new ExaDocumentLoaderException("Failed to build Exa request body", e);
        }
    }

    /**
     * Converts a single Exa search result JSON node into a {@link Document}.
     *
     * <p>
     * This method extracts the document text content and maps available result
     * fields into {@link Metadata}, including title, URL, Exa result ID,
     * publication date, author, and relevance score.
     * </p>
     *
     * <p>
     * Text content is resolved using {@link #extractText(JsonNode)} and metadata
     * fields are included only when present and non-blank, except for the score
     * which is always added.
     * </p>
     *
     * @param result the JSON node representing a single Exa search result
     * @return a {@link Document} containing the extracted text and metadata
     */
    private Document toDocument(JsonNode result) {
        String text = extractText(result);

        String title = result.path(FIELD_TITLE).asText(null);
        String url = result.path(FIELD_URL).asText(null);
        String id = result.path(FIELD_ID).asText(null);
        String publishedDate = result.path(FIELD_PUBLISHED_DATE).asText(null);
        String author = result.path(FIELD_AUTHOR).asText(null);
        double score = result.path(FIELD_SCORE).asDouble(0.0);

        Metadata metadata = new Metadata();

        if (!isNullOrBlank(title)) {
            metadata.put(METADATA_TITLE, title);
        }

        if (!isNullOrBlank(url)) {
            metadata.put(Document.URL, url);
        }

        if (!isNullOrBlank(id)) {
            metadata.put(METADATA_EXA_ID, id);
        }

        if (!isNullOrBlank(publishedDate)) {
            metadata.put(METADATA_PUBLISHED_DATE, publishedDate);
        }

        if (!isNullOrBlank(author)) {
            metadata.put(METADATA_AUTHOR, author);
        }

        metadata.put(METADATA_SCORE, score);

        return Document.from(text, metadata);
    }

    /**
     * Extracts the most relevant text content from a single Exa search result.
     *
     * <p>
     * The text is resolved using the following priority order:
     * <ol>
     * <li>the {@code text} field, if present</li>
     * <li>concatenated values from the {@code highlights} array</li>
     * <li>the {@code title} field as a fallback</li>
     * </ol>
     * </p>
     *
     * <p>
     * When highlights are used, each highlight is joined using a newline separator
     * and the final result is trimmed.
     * </p>
     *
     * @param result the JSON node representing a single Exa search result
     * @return the extracted text content, never {@code null}
     */
    private String extractText(JsonNode result) {
        JsonNode textNode = result.path(FIELD_TEXT);

        if (!textNode.isMissingNode() && !textNode.isNull()) {
            return textNode.asText("");
        }

        JsonNode highlights = result.path(FIELD_HIGHLIGHTS);

        if (highlights.isArray() && !highlights.isEmpty()) {
            StringBuilder builder = new StringBuilder();

            for (JsonNode highlight : highlights) {
                builder.append(highlight.asText()).append("\n");
            }

            return builder.toString().trim();
        }

        return result.path(FIELD_TITLE).asText("");
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating instances of {@link ExaDocumentLoader}.
     *
     * <p>
     * This builder allows configuration of the Exa API key, number of search
     * results, search type, text inclusion behavior, and optional custom
     * dependencies such as {@link HttpClient} and {@link ObjectMapper}.
     * </p>
     *
     * <p>
     * Default values:
     * </p>
     * <ul>
     * <li>{@code numResults}: {@code 10}</li>
     * <li>{@code searchType}: {@link ExaSearchType#AUTO}</li>
     * <li>{@code includeText}: {@code true}</li>
     * </ul>
     *
     * <p>
     * Example usage:
     * </p>
     *
     * <pre>{@code
     * ExaDocumentLoader loader = ExaDocumentLoader.builder().apiKey("your-api-key").numResults(5)
     * 		.searchType(ExaSearchType.AUTO).includeText(true).build();
     * }</pre>
     */
    public static class Builder {

        private String apiKey;
        private int numResults = 10;
        private ExaSearchType searchType = ExaSearchType.AUTO;
        private boolean includeText = true;

        private HttpClient httpClient;
        private ObjectMapper objectMapper;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder numResults(int numResults) {
            this.numResults = numResults;
            return this;
        }

        public Builder searchType(ExaSearchType searchType) {
            this.searchType = searchType;
            return this;
        }

        public Builder includeText(boolean includeText) {
            this.includeText = includeText;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public ExaDocumentLoader build() {
            return new ExaDocumentLoader(this);
        }
    }
}
