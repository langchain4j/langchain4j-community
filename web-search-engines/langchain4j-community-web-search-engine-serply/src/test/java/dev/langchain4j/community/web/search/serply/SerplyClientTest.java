package dev.langchain4j.community.web.search.serply;

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
import dev.langchain4j.internal.RetryUtils.RetryPolicy;
import dev.langchain4j.web.search.WebSearchRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class SerplyClientTest {

    private static final RetryPolicy FAST_RETRIES =
            RetryUtils.retryPolicyBuilder().maxRetries(2).delayMillis(1).build();

    private static final String EMPTY_RESPONSE = "{\"results\": []}";

    @Test
    void should_send_query_key_page_size_and_start_offset() {
        StubHttpClient httpClient = new StubHttpClient(request -> success(EMPTY_RESPONSE));
        SerplyClient client = new SerplyClient("secret", "https://api.serply.io", httpClient, FAST_RETRIES);

        client.search(WebSearchRequest.builder()
                .searchTerms("langchain4j java")
                .maxResults(5)
                .startPage(3)
                .build());

        assertThat(httpClient.requests).hasSize(1);
        HttpRequest request = httpClient.requests.get(0);
        assertThat(request.url()).startsWith("https://api.serply.io/v1/search?");
        assertThat(request.url()).contains("q=langchain4j+java", "num=5", "start=10");
        assertThat(request.headers()).containsEntry("X-Api-Key", List.of("secret"));
        assertThat(request.headers()).containsKey("User-Agent");
    }

    @Test
    void should_not_send_start_for_first_page_and_use_default_page_size_for_offsets() {
        StubHttpClient httpClient = new StubHttpClient(request -> success(EMPTY_RESPONSE));
        SerplyClient client = new SerplyClient("secret", "https://api.serply.io", httpClient, FAST_RETRIES);

        client.search(WebSearchRequest.from("first page"));
        client.search(WebSearchRequest.builder()
                .searchTerms("second page")
                .startPage(2)
                .build());

        assertThat(httpClient.requests.get(0).url()).doesNotContain("start=", "num=");
        assertThat(httpClient.requests.get(1).url()).contains("start=" + SerplyClient.DEFAULT_PAGE_SIZE);
        assertThat(httpClient.requests.get(1).url()).doesNotContain("num=");
    }

    @Test
    void should_fail_fast_on_authentication_errors_without_retrying() {
        StubHttpClient httpClient = new StubHttpClient(request -> {
            throw new HttpException(401, "{\"detail\":\"Invalid API key\"}");
        });
        SerplyClient client = new SerplyClient("bogus", "https://api.serply.io", httpClient, FAST_RETRIES);

        assertThatThrownBy(() -> client.search(WebSearchRequest.from("anything")))
                .isInstanceOf(AuthenticationException.class)
                .isInstanceOf(NonRetriableException.class)
                .hasMessage("Serply request failed with HTTP 401: {\"detail\":\"Invalid API key\"}")
                .hasCauseInstanceOf(HttpException.class);
        assertThat(httpClient.requests).hasSize(1);
    }

    @Test
    void should_fail_fast_on_other_client_errors() {
        StubHttpClient httpClient = new StubHttpClient(request -> {
            throw new HttpException(400, "bad request");
        });
        SerplyClient client = new SerplyClient("secret", "https://api.serply.io", httpClient, FAST_RETRIES);

        assertThatThrownBy(() -> client.search(WebSearchRequest.from("anything")))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("HTTP 400");
        assertThat(httpClient.requests).hasSize(1);
    }

    @Test
    void should_retry_server_errors_and_surface_status_when_exhausted() {
        StubHttpClient httpClient = new StubHttpClient(request -> {
            throw new HttpException(503, "upstream unavailable");
        });
        SerplyClient client = new SerplyClient("secret", "https://api.serply.io", httpClient, FAST_RETRIES);

        assertThatThrownBy(() -> client.search(WebSearchRequest.from("anything")))
                .isInstanceOf(InternalServerException.class)
                .hasMessage("Serply request failed with HTTP 503: upstream unavailable");
        assertThat(httpClient.requests).hasSize(3);
    }

    @Test
    void should_retry_rate_limits_and_succeed_once_the_server_recovers() {
        List<Integer> attempts = new ArrayList<>();
        StubHttpClient httpClient = new StubHttpClient(request -> {
            attempts.add(attempts.size() + 1);
            if (attempts.size() == 1) {
                throw new HttpException(429, "slow down");
            }
            return success(EMPTY_RESPONSE);
        });
        SerplyClient client = new SerplyClient("secret", "https://api.serply.io", httpClient, FAST_RETRIES);

        SerplyWebSearchResponse response = client.search(WebSearchRequest.from("anything"));

        assertThat(response.getResults()).isEmpty();
        assertThat(attempts).hasSize(2);
    }

    @Test
    void should_map_rate_limit_to_retriable_exception_and_truncate_long_bodies() {
        String longBody = "x".repeat(700);

        RuntimeException mapped = SerplyClient.toException(new HttpException(429, longBody));

        assertThat(mapped).isInstanceOf(RateLimitException.class);
        assertThat(mapped.getMessage())
                .startsWith("Serply request failed with HTTP 429: ")
                .endsWith("...");
        assertThat(mapped.getMessage().length()).isLessThan(longBody.length());
    }

    @Test
    void should_not_retry_unparseable_bodies() {
        StubHttpClient httpClient = new StubHttpClient(request -> success("<html>not json</html>"));
        SerplyClient client = new SerplyClient("secret", "https://api.serply.io", httpClient, FAST_RETRIES);

        assertThatThrownBy(() -> client.search(WebSearchRequest.from("anything")))
                .isInstanceOf(NonRetriableException.class)
                .hasMessage("Failed to parse Serply response");
        assertThat(httpClient.requests).hasSize(1);
    }

    private static SuccessfulHttpResponse success(String body) {
        return SuccessfulHttpResponse.builder()
                .statusCode(200)
                .body(body.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    private static class StubHttpClient implements HttpClient {

        private final Function<HttpRequest, SuccessfulHttpResponse> handler;
        private final List<HttpRequest> requests = new ArrayList<>();

        private StubHttpClient(Function<HttpRequest, SuccessfulHttpResponse> handler) {
            this.handler = handler;
        }

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) throws HttpException {
            requests.add(request);
            return handler.apply(request);
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
