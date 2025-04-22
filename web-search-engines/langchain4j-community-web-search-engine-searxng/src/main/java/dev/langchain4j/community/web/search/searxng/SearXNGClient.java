package dev.langchain4j.community.web.search.searxng;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.http.client.HttpMethod.GET;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import dev.langchain4j.web.search.WebSearchRequest;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

class SearXNGClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);
    private final Map<String, Object> optionalParams;

    private final HttpClient httpClient;

    private final String baseUrl;

    public SearXNGClient(
            String baseUrl,
            Duration timeout,
            boolean logRequests,
            boolean logResponses,
            Map<String, Object> optionalParams) {
        this.optionalParams = optionalParams;
        this.baseUrl = baseUrl;
        HttpClientBuilder httpClientBuilder = HttpClientBuilderLoader.loadHttpClientBuilder();
        HttpClient client =
                httpClientBuilder.connectTimeout(timeout).readTimeout(timeout).build();
        if (logRequests || logResponses) {
            this.httpClient = new LoggingHttpClient(client, logRequests, logResponses);
        } else {
            this.httpClient = client;
        }
    }

    SearXNGResponse search(WebSearchRequest request) {
        try {
            final Map<String, Object> args = new HashMap<>();
            if (optionalParams != null) {
                args.putAll(optionalParams);
            }
            if (request.additionalParams() != null) {
                args.putAll(request.additionalParams());
            }
            args.put("q", request.searchTerms());
            args.put("format", "json");
            // Only consider explicit safesearch requests, otherwise keep the default
            if (request.safeSearch() != null) {
                if (request.safeSearch()) {
                    // Set to strict as opposed to moderate
                    args.put("safesearch", 2);
                } else {
                    args.put("safesearch", 0);
                }
            }
            if (request.startPage() != null) {
                args.put("pageno", request.startPage());
            }
            if (request.language() != null) {
                args.put("language", request.language());
            }
            HttpRequest httpRequest = HttpRequest.builder()
                    .method(GET)
                    .url(baseUrl, Utils.pathWithQuery("search", args))
                    .addHeader("Content-Type", "application/json")
                    .build();
            SuccessfulHttpResponse response = httpClient.execute(httpRequest);
            return OBJECT_MAPPER.readValue(response.body(), SearXNGResponse.class);
        } catch (IOException | HttpException e) {
            throw new RuntimeException(e);
        }
    }
}
