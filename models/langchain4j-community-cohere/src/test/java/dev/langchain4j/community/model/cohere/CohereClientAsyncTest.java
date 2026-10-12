package dev.langchain4j.community.model.cohere;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.langchain4j.community.model.client.CohereClient;
import dev.langchain4j.community.model.client.chat.CohereChatRequest;
import dev.langchain4j.community.model.client.chat.message.CohereUserMessage;
import dev.langchain4j.community.model.client.chat.response.CohereChatResponse;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpMethod;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

class CohereClientAsyncTest {

    @Test
    void should_dispatch_without_blocking_and_parse_the_response() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        CompletableFuture<SuccessfulHttpResponse> httpFuture = new CompletableFuture<>();
        when(httpClient.executeAsync(any())).thenReturn(httpFuture);

        CompletableFuture<CohereChatResponse> result =
                client(httpClient, false, false).createMessageAsync(request());

        assertThat(result).isNotDone();
        verify(httpClient, never()).execute(any(HttpRequest.class));
        ArgumentCaptor<HttpRequest> captured = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).executeAsync(captured.capture());
        assertThat(captured.getValue().method()).isEqualTo(HttpMethod.POST);
        assertThat(captured.getValue().url()).isEqualTo("http://localhost/v2/chat");
        assertThat(captured.getValue().body()).contains("command-r", "Hello");

        httpFuture.complete(response("{\"id\":\"response-id\"}"));

        assertThat(result.get(5, SECONDS).getId()).isEqualTo("response-id");
    }

    @Test
    void should_use_the_same_http_request_as_the_synchronous_path() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        SuccessfulHttpResponse response = response("{\"id\":\"response-id\"}");
        when(httpClient.execute(any(HttpRequest.class))).thenReturn(response);
        when(httpClient.executeAsync(any())).thenReturn(CompletableFuture.completedFuture(response));
        CohereClient client = client(httpClient, false, false);

        CohereChatResponse synchronous = client.createMessage(request());
        CohereChatResponse asynchronous = client.createMessageAsync(request()).get(5, SECONDS);

        ArgumentCaptor<HttpRequest> syncRequest = ArgumentCaptor.forClass(HttpRequest.class);
        ArgumentCaptor<HttpRequest> asyncRequest = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).execute(syncRequest.capture());
        verify(httpClient).executeAsync(asyncRequest.capture());
        assertThat(asyncRequest.getValue()).usingRecursiveComparison().isEqualTo(syncRequest.getValue());
        assertThat(asynchronous).isEqualTo(synchronous);
    }

    @Test
    void should_preserve_the_http_failure() {
        HttpClient httpClient = mock(HttpClient.class);
        HttpException failure = new HttpException(429, "rate limited");
        when(httpClient.executeAsync(any())).thenReturn(CompletableFuture.failedFuture(failure));

        CompletableFuture<CohereChatResponse> result =
                client(httpClient, false, false).createMessageAsync(request());

        assertThatThrownBy(() -> result.get(5, SECONDS)).hasCause(failure);
    }

    @Test
    void should_complete_exceptionally_when_response_json_is_invalid() {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.executeAsync(any())).thenReturn(CompletableFuture.completedFuture(response("not json")));

        CompletableFuture<CohereChatResponse> result =
                client(httpClient, false, false).createMessageAsync(request());

        assertThatThrownBy(() -> result.get(5, SECONDS)).isInstanceOf(ExecutionException.class);
    }

    @Test
    void should_preserve_unsupported_async_instead_of_falling_back_to_sync() {
        HttpClient httpClient = mock(HttpClient.class, org.mockito.Mockito.CALLS_REAL_METHODS);

        CompletableFuture<CohereChatResponse> result =
                client(httpClient, false, false).createMessageAsync(request());

        assertThatThrownBy(() -> result.get(5, SECONDS)).hasCauseInstanceOf(AsyncNotSupportedException.class);
        verify(httpClient, never()).execute(any(HttpRequest.class));
    }

    @ParameterizedTest(name = "logRequests={0}, logResponses={1}, mayInterruptIfRunning={2}")
    @CsvSource({
        "false,false,false", "false,false,true", "false,true,false", "false,true,true",
        "true,false,false", "true,false,true", "true,true,false", "true,true,true"
    })
    void should_propagate_cancellation_to_the_http_future(
            boolean logRequests, boolean logResponses, boolean mayInterruptIfRunning) {
        HttpClient httpClient = mock(HttpClient.class);
        CompletableFuture<SuccessfulHttpResponse> httpFuture = new CompletableFuture<>();
        when(httpClient.executeAsync(any())).thenReturn(httpFuture);

        CompletableFuture<CohereChatResponse> result =
                client(httpClient, logRequests, logResponses).createMessageAsync(request());
        result.cancel(mayInterruptIfRunning);

        assertThat(result).isCancelled();
        assertThat(httpFuture).isCancelled();
    }

    @Test
    void should_remain_cancelled_when_an_uncancellable_transport_completes_later() {
        HttpClient httpClient = mock(HttpClient.class);
        CompletableFuture<SuccessfulHttpResponse> httpFuture = new CompletableFuture<>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }
        };
        when(httpClient.executeAsync(any())).thenReturn(httpFuture);

        CompletableFuture<CohereChatResponse> result =
                client(httpClient, false, false).createMessageAsync(request());
        result.cancel(true);
        httpFuture.complete(response("{\"id\":\"late-response\"}"));

        assertThat(result).isCancelled();
    }

    private static CohereClient client(HttpClient httpClient, boolean logRequests, boolean logResponses) {
        HttpClientBuilder builder = mock(HttpClientBuilder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(httpClient);
        return CohereClient.builder()
                .httpClientBuilder(builder)
                .baseUrl("http://localhost/v2/")
                .authToken("test-api-key")
                .logRequests(logRequests)
                .logResponses(logResponses)
                .logger(mock(Logger.class))
                .build();
    }

    private static CohereChatRequest request() {
        return CohereChatRequest.builder()
                .model("command-r")
                .messages(List.of(CohereUserMessage.from("Hello")))
                .build();
    }

    private static SuccessfulHttpResponse response(String body) {
        return SuccessfulHttpResponse.builder()
                .statusCode(200)
                .headers(Map.of())
                .body(body)
                .build();
    }
}
