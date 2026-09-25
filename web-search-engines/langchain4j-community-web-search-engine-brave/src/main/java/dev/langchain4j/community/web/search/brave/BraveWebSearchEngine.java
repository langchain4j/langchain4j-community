package dev.langchain4j.community.web.search.brave;

import static dev.langchain4j.internal.UriUtils.createUriSafely;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.time.Duration.ofSeconds;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchInformationResult;
import dev.langchain4j.web.search.WebSearchOrganicResult;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.WebSearchResults;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents Brave Search API as a {@code WebSearchEngine}.
 * See more details <a href="https://api-dashboard.search.brave.com/documentation">here</a>.
 * <br>
 * The Brave web search endpoint returns at most 20 results per page, so {@link WebSearchRequest#maxResults()}
 * values above 20 are clamped to 20.
 * Pagination is limited to 10 pages, so {@link WebSearchRequest#startPage()} values above 10 are clamped to 10.
 * Whether further results are available can be checked in the
 * {@link WebSearchResults#searchMetadata()} key {@code moreResultsAvailable}.
 * <br>
 * An explicitly disabled {@link WebSearchRequest#safeSearch()} is mapped to Brave's {@code off} value.
 * An ordinary {@link WebSearchRequest} leaves Brave's default {@code moderate} setting in place; use the
 * {@code safesearch} additional parameter when {@code strict} is required.
 * <br>
 * Brave-specific parameters (e.g. {@code freshness}, {@code spellcheck}, {@code extra_snippets}, {@code units},
 * {@code ui_lang}, {@code goggles}) can be passed through {@link WebSearchRequest#additionalParams()}.
 */
public class BraveWebSearchEngine implements WebSearchEngine {

    private static final String DEFAULT_BASE_URL = "https://api.search.brave.com";
    private static final int MAX_COUNT = 20;
    private static final int MAX_OFFSET = 9;

    private final BraveClient braveClient;

    public BraveWebSearchEngine(BraveWebSearchEngineBuilder builder) {
        this.braveClient = BraveClient.builder()
                .httpClientBuilder(builder.httpClientBuilder)
                .baseUrl(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL))
                .apiKey(ensureNotBlank(builder.apiKey, "apiKey"))
                .timeout(getOrDefault(builder.timeout, ofSeconds(30)))
                .logRequests(builder.logRequests)
                .logResponses(builder.logResponses)
                .build();
    }

    public static BraveWebSearchEngineBuilder builder() {
        return new BraveWebSearchEngineBuilder();
    }

    @Override
    public WebSearchResults search(WebSearchRequest webSearchRequest) {
        ensureNotNull(webSearchRequest, "webSearchRequest");

        BraveWebSearchRequest braveRequest = toBraveWebSearchRequest(webSearchRequest);
        BraveWebSearchResponse response = braveClient.search(braveRequest);

        List<WebSearchOrganicResult> results = toWebSearchOrganicResults(response);

        Map<String, Object> searchMetadata = new HashMap<>();
        if (response.getQuery() != null && response.getQuery().getMoreResultsAvailable() != null) {
            searchMetadata.put("moreResultsAvailable", response.getQuery().getMoreResultsAvailable());
        }

        return WebSearchResults.from(
                searchMetadata,
                WebSearchInformationResult.from(totalResults(braveRequest, results), pageNumber(braveRequest), null),
                results);
    }

    static BraveWebSearchRequest toBraveWebSearchRequest(WebSearchRequest webSearchRequest) {
        Integer count = null;
        if (webSearchRequest.maxResults() != null) {
            count = Math.min(Math.max(webSearchRequest.maxResults(), 1), MAX_COUNT);
        }

        Integer offset = null;
        if (webSearchRequest.startPage() != null && webSearchRequest.startPage() > 1) {
            int pageSize = count != null ? count : MAX_COUNT;
            long requestedOffset = (long) (webSearchRequest.startPage() - 1) * pageSize;
            offset = (int) Math.min(requestedOffset, MAX_OFFSET);
        }

        String safesearch = null;
        if (Boolean.FALSE.equals(webSearchRequest.safeSearch())) {
            safesearch = "off";
        }

        Map<String, Object> additionalParameters = copyIfNotNull(webSearchRequest.additionalParams());
        String freshness = stringParameter(additionalParameters, BraveWebSearchRequest.FRESHNESS);
        Boolean spellcheck = booleanParameter(additionalParameters, BraveWebSearchRequest.SPELLCHECK);
        Boolean extraSnippets = booleanParameter(additionalParameters, BraveWebSearchRequest.EXTRA_SNIPPETS);

        return BraveWebSearchRequest.builder()
                .query(webSearchRequest.searchTerms())
                .count(count)
                .offset(offset)
                .language(webSearchRequest.language())
                .country(webSearchRequest.geoLocation())
                .safesearch(safesearch)
                .freshness(freshness)
                .spellcheck(spellcheck)
                .extraSnippets(extraSnippets)
                .additionalParameters(additionalParameters)
                .build();
    }

    private static Integer pageNumber(BraveWebSearchRequest request) {
        int pageSize = request.getCount() != null ? request.getCount() : MAX_COUNT;
        int offset = request.getOffset() != null ? request.getOffset() : 0;
        return offset / pageSize + 1;
    }

    private static long totalResults(BraveWebSearchRequest request, List<WebSearchOrganicResult> results) {
        // Brave does not expose a total count. The number through the returned page is the closest useful value.
        int offset = request.getOffset() != null ? request.getOffset() : 0;
        return (long) offset + results.size();
    }

    private static String stringParameter(Map<String, Object> parameters, String name) {
        Object value = parameters == null ? null : parameters.get(name);
        return value == null ? null : String.valueOf(value);
    }

    private static Boolean booleanParameter(Map<String, Object> parameters, String name) {
        Object value = parameters == null ? null : parameters.get(name);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value == null ? null : Boolean.valueOf(String.valueOf(value));
    }

    private static List<WebSearchOrganicResult> toWebSearchOrganicResults(BraveWebSearchResponse response) {
        List<BraveSearchResult> braveResults =
                response.getWeb() == null ? null : response.getWeb().getResults();

        if (isNullOrEmpty(braveResults)) {
            return new ArrayList<>();
        }

        return braveResults.stream()
                .map(BraveWebSearchEngine::toWebSearchOrganicResult)
                .filter(Objects::nonNull)
                .collect(toList());
    }

    private static WebSearchOrganicResult toWebSearchOrganicResult(BraveSearchResult braveResult) {
        // Skip results whose link cannot be resolved into a URI,
        // so a single unresolvable link does not abort the whole search.
        URI url = createUriSafely(braveResult.getUrl());
        if (url == null || isNullOrEmpty(braveResult.getTitle())) {
            return null;
        }

        Map<String, String> metadata = new HashMap<>();
        if (isNotNullOrBlank(braveResult.getPageAge())) {
            metadata.put("page_age", braveResult.getPageAge());
        }

        String content = isNullOrEmpty(braveResult.getExtraSnippets())
                ? null
                : String.join(System.lineSeparator(), braveResult.getExtraSnippets());

        return WebSearchOrganicResult.from(
                braveResult.getTitle(), url, braveResult.getDescription(), content, metadata);
    }

    public static WebSearchEngine withApiKey(String apiKey) {
        return builder().apiKey(apiKey).build();
    }

    public static class BraveWebSearchEngineBuilder extends BraveBuilder<BraveWebSearchEngineBuilder> {

        BraveWebSearchEngineBuilder() {}

        public BraveWebSearchEngine build() {
            return new BraveWebSearchEngine(this);
        }
    }
}
