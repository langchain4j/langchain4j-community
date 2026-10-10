package dev.langchain4j.community.web.search.serpapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.NonRetriableException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import dev.langchain4j.internal.RetryUtils;
import dev.langchain4j.web.search.WebSearchRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class SerpApiClientTest {

    private static final String RESULTS_JSON = """
            {"organic_results": [{"title": "LangChain4j", "link": "https://docs.langchain4j.dev/", "snippet": "Java"}]}
            """;

    @Test
    void should_map_request_fields_for_google() {
        HttpRequest request = client("google", Map.of("google_domain", "google.co.in", "hl", "de"), null)
                .buildRequest(WebSearchRequest.builder()
                        .searchTerms("What is LangChain4j?")
                        .language("en")
                        .geoLocation("in")
                        .startPage(3)
                        .safeSearch(true)
                        .additionalParams(Map.of("gl", "us"))
                        .build());

        assertThat(request.url())
                .startsWith("https://serpapi.com/search?")
                .contains("google_domain=google.co.in")
                .contains("hl=en") // request field beats default parameter
                .contains("gl=us") // additionalParams beat request fields
                .contains("start=20")
                .contains("safe=active")
                .contains("location=New+Delhi")
                .contains("engine=google")
                .contains("q=What+is+LangChain4j%3F")
                .contains("api_key=test-key");
    }

    @Test
    void should_use_search_query_for_youtube_and_skip_unsupported_params() {
        HttpRequest request = client("youtube", null, null)
                .buildRequest(WebSearchRequest.builder()
                        .searchTerms("langchain4j tutorial")
                        .startPage(2)
                        .safeSearch(true)
                        .build());

        assertThat(request.url())
                .contains("search_query=langchain4j+tutorial")
                .doesNotContain("q=")
                .doesNotContain("start=")
                .doesNotContain("safe=")
                .doesNotContain("location=");
    }

    @Test
    void should_prefer_start_index_over_start_page() {
        assertThat(SerpApiClient.startOffset(3, 5)).isEqualTo(5);
        assertThat(SerpApiClient.startOffset(3, null)).isEqualTo(20);
        assertThat(SerpApiClient.startOffset(null, null)).isZero();
    }

    @Test
    void should_map_http_errors_to_typed_exceptions() {
        assertThat(SerpApiClient.toException(new HttpException(400, "{\"error\": \"Missing query `q` parameter.\"}")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Missing query `q` parameter.");
        assertThat(SerpApiClient.toException(new HttpException(401, "{\"error\": \"Invalid API key.\"}")))
                .isInstanceOf(AuthenticationException.class);
        assertThat(SerpApiClient.toException(new HttpException(429, "{\"error\": \"Too many requests\"}")))
                .isInstanceOf(RateLimitException.class);
        assertThat(SerpApiClient.toException(
                        new HttpException(429, "{\"error\": \"Your account has run out of searches.\"}")))
                .isInstanceOf(NonRetriableException.class);
        assertThat(SerpApiClient.toException(new HttpException(503, ""))).isInstanceOf(InternalServerException.class);
    }

    @Test
    void should_not_retry_when_out_of_searches() {
        StubHttpClient http = new StubHttpClient(request -> {
            throw new HttpException(429, "{\"error\": \"Your account has run out of searches.\"}");
        });

        assertThatThrownBy(() -> client("google", null, http).search(WebSearchRequest.from("q")))
                .isInstanceOf(NonRetriableException.class)
                .hasMessageContaining("run out of searches");
        assertThat(http.calls).isEqualTo(1);
    }

    @Test
    void should_not_retry_authentication_errors() {
        StubHttpClient http = new StubHttpClient(request -> {
            throw new HttpException(401, "{\"error\": \"Invalid API key.\"}");
        });

        assertThatThrownBy(() -> client("google", null, http).search(WebSearchRequest.from("q")))
                .isInstanceOf(AuthenticationException.class);
        assertThat(http.calls).isEqualTo(1);
    }

    @Test
    void should_retry_server_errors() {
        StubHttpClient http = new StubHttpClient(request -> {
            throw new HttpException(503, "{\"error\": \"Please try again later.\"}");
        });

        assertThatThrownBy(() -> client("google", null, http).search(WebSearchRequest.from("q")))
                .isInstanceOf(InternalServerException.class);
        assertThat(http.calls).isEqualTo(3);
    }

    @Test
    void should_fail_on_error_in_successful_response() {
        assertThatThrownBy(() -> SerpApiClient.parse("{\"error\": \"Unsupported engine\"}"))
                .isInstanceOf(InternalServerException.class)
                .hasMessageContaining("Unsupported engine");
        assertThatThrownBy(() -> SerpApiClient.parse("not json")).isInstanceOf(NonRetriableException.class);
    }

    @Test
    void should_search_async() throws Exception {
        StubHttpClient http = new StubHttpClient(request -> response(RESULTS_JSON));

        assertThat(client("google", null, http)
                        .searchAsync(WebSearchRequest.from("q"))
                        .get()
                        .path("organic_results")
                        .size())
                .isEqualTo(1);
    }

    @Test
    void should_map_async_http_errors() {
        StubHttpClient http = new StubHttpClient(request -> {
            throw new HttpException(401, "{\"error\": \"Invalid API key.\"}");
        });

        assertThatThrownBy(() -> client("google", null, http)
                        .searchAsync(WebSearchRequest.from("q"))
                        .get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(AuthenticationException.class);
    }

    @Test
    void should_propagate_async_cancellation() {
        CompletableFuture<SuccessfulHttpResponse> pending = new CompletableFuture<>();
        StubHttpClient http = new StubHttpClient(request -> null) {
            @Override
            public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
                return pending;
            }
        };

        client("google", null, http).searchAsync(WebSearchRequest.from("q")).cancel(true);

        assertThat(pending).isCancelled();
    }

    @Test
    void should_mask_api_key_in_logged_urls() {
        assertThat(SerpApiClient.MaskingLoggingHttpClient.mask(
                        "https://serpapi.com/search?engine=google&api_key=secret123&q=x"))
                .isEqualTo("https://serpapi.com/search?engine=google&api_key=****&q=x");
    }

    private static SerpApiClient client(String engine, Map<String, Object> defaults, HttpClient httpClient) {
        return new SerpApiClient(
                "test-key",
                SerpApiClient.defaultBaseUrl(),
                engine,
                "New Delhi",
                false,
                defaults,
                httpClient,
                RetryUtils.retryPolicyBuilder().maxRetries(2).delayMillis(0).build());
    }

    private static SuccessfulHttpResponse response(String body) {
        return SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of("content-type", List.of("application/json")))
                .body(body)
                .build();
    }

    private static class StubHttpClient implements HttpClient {

        private final Function<HttpRequest, SuccessfulHttpResponse> handler;
        int calls;

        StubHttpClient(Function<HttpRequest, SuccessfulHttpResponse> handler) {
            this.handler = handler;
        }

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            calls++;
            return handler.apply(request);
        }

        @Override
        public CompletableFuture<SuccessfulHttpResponse> executeAsync(HttpRequest request) {
            try {
                return CompletableFuture.completedFuture(execute(request));
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
