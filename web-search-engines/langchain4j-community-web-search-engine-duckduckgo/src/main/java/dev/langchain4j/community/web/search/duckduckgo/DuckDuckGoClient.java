package dev.langchain4j.community.web.search.duckduckgo;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.http.client.HttpMethod.POST;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.web.search.WebSearchRequest;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

class DuckDuckGoClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);
    private static final String HTML_SEARCH_URL = "https://html.duckduckgo.com/html/";
    private static final String API_SEARCH_URL = "https://api.duckduckgo.com/";

    private final HttpClient httpClient;

    public DuckDuckGoClient(Duration timeout, boolean logRequests, boolean logResponses) {
        HttpClientBuilder builder = HttpClientBuilderLoader.loadHttpClientBuilder()
                .connectTimeout(timeout)
                .readTimeout(timeout);

        HttpClient client = builder.build();
        this.httpClient =
                (logRequests || logResponses) ? new LoggingHttpClient(client, logRequests, logResponses) : client;
    }

    List<DuckDuckGoSearchResult> search(WebSearchRequest request) {
        return RetryUtils.retryPolicyBuilder()
                .maxRetries(3)
                .delayMillis(1500)
                .build()
                .withRetry(() -> {
                    List<DuckDuckGoSearchResult> results;
                    try {
                        results = performHtmlSearch(request);
                    } catch (Exception e) {
                        results = List.of();
                    }

                    if (results.isEmpty()) {
                        results = performApiSearch(request);
                    }

                    if (results.isEmpty()) {
                        var term = request.searchTerms() != null ? request.searchTerms() : "search";
                        return List.of(DuckDuckGoSearchResult.builder()
                                .title("No DuckDuckGo Search Result was found")
                                .url("https://duckduckgo.com/")
                                .snippet("No DuckDuckGo Search Result was found.")
                                .build());
                    }
                    return results;
                });
    }

    CompletableFuture<List<DuckDuckGoSearchResult>> searchAsync(WebSearchRequest request) {
        return CompletableFuture.supplyAsync(() -> search(request));
    }

    private List<DuckDuckGoSearchResult> performHtmlSearch(WebSearchRequest request) throws IOException {
        Map<String, Object> formData = buildSearchParams(request);
        String formBody = Utils.buildFormData(formData);

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(HTML_SEARCH_URL)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .body(formBody)
                .build();

        SuccessfulHttpResponse response = httpClient.execute(httpRequest);

        if (response.statusCode() == 202) {
            throw new IOException("DuckDuckGo HTML search blocked");
        }

        Document doc = Jsoup.parse(response.body());
        int limit = request.maxResults() != null ? request.maxResults() : 10;

        List<DuckDuckGoSearchResult> results = parseHtmlResults(doc, limit);

        if (results.isEmpty()) {
            throw new IOException("No DuckDuckGo HTML search results found");
        }

        return results;
    }

    private List<DuckDuckGoSearchResult> performApiSearch(WebSearchRequest request) {
        String url = API_SEARCH_URL + "?q=" + Utils.urlEncode(request.searchTerms())
                + "&format=json&no_html=1&skip_disambig=1";

        HttpRequest httpRequest = HttpRequest.builder()
                .method(GET)
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Accept", "application/json")
                .build();

        SuccessfulHttpResponse response = httpClient.execute(httpRequest);
        return parseApiResponse(response.body(), request.maxResults());
    }

    private Map<String, Object> buildSearchParams(WebSearchRequest request) {
        Map<String, Object> params = new HashMap<>();
        params.put("q", request.searchTerms());
        params.put("b", "");
        params.put("kl", "us-en");

        if (request.language() != null) {
            params.put("kl", request.language());
        }

        if (request.safeSearch() != null) {
            params.put("safe", request.safeSearch() ? "strict" : "off");
        }

        if (request.additionalParams() != null) {
            params.putAll(request.additionalParams());
        }

        return params;
    }

    private List<DuckDuckGoSearchResult> parseHtmlResults(Document doc, int maxResults) {
        List<DuckDuckGoSearchResult> results = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        String[] containers = new String[] {
            "div.result__body",
            "div.web-result",
            "div.result",
            ".links_main",
            "div[data-testid='result']",
            "div.nrn-react-div"
        };

        Elements resultElements = new Elements();
        for (String sel : containers) {
            resultElements = doc.select(sel);
            if (!resultElements.isEmpty()) break;
        }

        if (resultElements.isEmpty()) {
            resultElements = doc.select("a.result__a, .result__a");
        }

        for (Element element : resultElements) {
            if (results.size() >= maxResults) break;

            if ("a".equalsIgnoreCase(element.tagName()) && element.hasAttr("href")) {
                String title = element.text().trim();
                String url = cleanUrl(element.attr("href"));
                if (!title.isEmpty() && isValidUrl(url) && seenUrls.add(url)) {
                    results.add(DuckDuckGoSearchResult.builder()
                            .title(title)
                            .url(url)
                            .snippet(title)
                            .build());
                }
                continue;
            }

            Element titleElement = element.selectFirst("h2 a, .result__title a, h3 a, a.result__a, .result__a");
            if (titleElement == null) {
                titleElement = element.selectFirst("a[href]");
            }

            if (titleElement == null) continue;

            String title = titleElement.text().trim();
            String url = cleanUrl(titleElement.attr("href"));

            if (title.isEmpty() || !isValidUrl(url) || !seenUrls.add(url)) continue;

            String snippet = "";
            Element snippetElement =
                    element.selectFirst(".result__snippet, .snippet, .result-snippet, [data-testid='result-snippet']");
            if (snippetElement != null) {
                snippet = snippetElement.text().trim();
            }

            if (snippet.isEmpty()) {
                StringBuilder bodyFromLinks = new StringBuilder();
                Elements links = element.select("a");
                for (Element link : links) {
                    String linkText = link.text().trim();
                    if (!linkText.isEmpty() && !linkText.equals(title)) {
                        if (!bodyFromLinks.isEmpty()) bodyFromLinks.append(" ");
                        bodyFromLinks.append(linkText);
                    }
                }
                snippet = bodyFromLinks.toString();
            }

            if (snippet.isEmpty()) {
                String elementText = element.text().trim();
                if (!elementText.equals(title) && elementText.length() > title.length()) {
                    snippet = elementText.replace(title, "").trim();
                    if (snippet.length() > 200) {
                        snippet = snippet.substring(0, 200) + "...";
                    }
                }
            }

            if (snippet.isEmpty()) {
                snippet = title;
            }

            results.add(DuckDuckGoSearchResult.builder()
                    .title(title)
                    .url(url)
                    .snippet(snippet)
                    .build());
        }

        return results;
    }

    private void addField(JsonNode root, List<DuckDuckGoSearchResult> results, String field, int limit) {
        String value = getJsonText(root, field);
        if (!value.isEmpty() && results.size() < limit) {
            results.add(DuckDuckGoSearchResult.builder()
                    .title(field)
                    .url("https://duckduckgo.com")
                    .snippet(value)
                    .build());
        }
    }

    private List<DuckDuckGoSearchResult> parseApiResponse(String json, Integer maxResults) {
        List<DuckDuckGoSearchResult> results = new ArrayList<>();
        int limit = maxResults != null ? maxResults : 10;

        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            addField(root, results, "Heading", limit);
            addField(root, results, "AbstractText", limit);
            addField(root, results, "Abstract", limit);
            addField(root, results, "Answer", limit);

            // we parse the results from, RelatedTopics array
            JsonNode relatedTopics = root.get("RelatedTopics");
            if (relatedTopics != null && relatedTopics.isArray()) {
                for (JsonNode topic : relatedTopics) {
                    if (results.size() >= limit) break;

                    if (topic.has("Topics")) {
                        for (JsonNode nested : topic.get("Topics")) {
                            addRelatedTopicResult(nested, results);
                            if (results.size() >= limit) break;
                        }
                    } else {
                        addRelatedTopicResult(topic, results);
                    }
                }
            }

            // we parse the results from "Results" array
            JsonNode apiResults = root.get("Results");
            if (apiResults != null && apiResults.isArray()) {
                for (JsonNode r : apiResults) {
                    if (results.size() >= limit) break;
                    String url = getJsonText(r, "FirstURL");
                    String text = getJsonText(r, "Text");
                    if (!url.isEmpty() && !text.isEmpty()) {
                        results.add(DuckDuckGoSearchResult.builder()
                                .title(text)
                                .url(url)
                                .snippet(text)
                                .build());
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return results.stream().limit(limit).collect(Collectors.toList());
    }

    private boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        if (url.contains("duckduckgo.com") && !url.contains("/l/")) return false;
        return url.startsWith("https://") || url.startsWith("http://") || url.startsWith("//");
    }

    private String cleanUrl(String url) {
        if (url.startsWith("//")) {
            url = "https:" + url;
        }
        if (url.startsWith("http://")) {
            url = url.replace("http://", "https://");
        }
        return url;
    }

    private String getJsonText(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asText("").trim() : "";
    }

    private void addRelatedTopicResult(JsonNode topic, List<DuckDuckGoSearchResult> results) {
        String url = getJsonText(topic, "FirstURL");
        String text = getJsonText(topic, "Text");
        if (!url.isEmpty() && !text.isEmpty()) {
            results.add(DuckDuckGoSearchResult.builder()
                    .title(text)
                    .url(url)
                    .snippet(text)
                    .build());
        }
    }
}
