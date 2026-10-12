package dev.langchain4j.community.model.cohere;

import static dev.langchain4j.internal.Json.fromJson;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.community.model.CohereChatModel;
import dev.langchain4j.community.model.client.CohereChatRequestParameters;
import dev.langchain4j.community.model.client.chat.response.CohereChatResponseMetadata;
import dev.langchain4j.data.message.AudioContent;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class CohereChatModelAsyncTest {

    private static final String RESPONSE = """
            {
              "id": "response-id",
              "message": {"role": "assistant", "content": [{"type": "text", "text": "Hello"}]},
              "finish_reason": "COMPLETE",
              "usage": {"tokens": {"input_tokens": 3, "output_tokens": 2},
                        "billed_units": {"input_tokens": 3, "output_tokens": 2}, "cached_tokens": 1}
            }
            """;

    private HttpServer server;
    private ExecutorService executor;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void should_return_a_chat_response_asynchronously() throws Exception {
        server.createContext("/v2/chat", exchange -> respond(exchange, 200, RESPONSE));
        CohereChatModel model = modelBuilder().build();

        ChatResponse response = model.chatAsync(request()).get(5, SECONDS);

        assertThat(response.aiMessage().text()).isEqualTo("Hello");
        assertThat(response.metadata().id()).isEqualTo("response-id");
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(3);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(2);
        assertThat(((CohereChatResponseMetadata) response.metadata()).cachedTokens())
                .isEqualTo(1);
    }

    @Test
    void should_start_the_native_http_call_on_the_calling_thread() {
        HttpClient httpClient = mock(HttpClient.class);
        CompletableFuture<SuccessfulHttpResponse> transport = new CompletableFuture<>();
        AtomicReference<Thread> dispatchThread = new AtomicReference<>();
        when(httpClient.executeAsync(any())).thenAnswer(invocation -> {
            dispatchThread.set(Thread.currentThread());
            return transport;
        });
        CohereChatModel model = modelUsing(httpClient, 0);

        CompletableFuture<ChatResponse> result = model.chatAsync(request());

        try {
            assertThat(result).isNotDone();
            assertThat(dispatchThread.get()).isSameAs(Thread.currentThread());
            verify(httpClient).executeAsync(any());
            verify(httpClient, never()).execute(any(HttpRequest.class));
        } finally {
            result.cancel(true);
        }
    }

    @Test
    void should_cancel_an_attempt_created_during_retry_transition() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch secondAttemptStarted = new CountDownLatch(1);
        CountDownLatch returnSecondAttempt = new CountDownLatch(1);
        CompletableFuture<SuccessfulHttpResponse> secondTransport = new CompletableFuture<>();
        when(httpClient.executeAsync(any())).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                return CompletableFuture.failedFuture(new HttpException(429, "retry"));
            }
            secondAttemptStarted.countDown();
            if (!returnSecondAttempt.await(5, SECONDS)) {
                throw new AssertionError("test must release the second attempt");
            }
            return secondTransport;
        });
        CompletableFuture<ChatResponse> result = modelUsing(httpClient, 1).chatAsync(request());

        try {
            assertThat(secondAttemptStarted.await(5, SECONDS)).isTrue();
            result.cancel(true);
            returnSecondAttempt.countDown();

            assertThatThrownBy(() -> secondTransport.get(5, SECONDS)).isInstanceOf(CancellationException.class);
            assertThat(result).isCancelled();
            assertThat(attempts).hasValue(2);
        } finally {
            returnSecondAttempt.countDown();
            result.cancel(true);
        }
    }

    private CohereChatModel modelUsing(HttpClient httpClient, int maxRetries) {
        HttpClientBuilder builder = mock(HttpClientBuilder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(httpClient);
        try (var loader = mockStatic(HttpClientBuilderLoader.class)) {
            loader.when(HttpClientBuilderLoader::loadHttpClientBuilder).thenReturn(builder);
            return modelBuilder().maxRetries(maxRetries).build();
        }
    }

    @Test
    void should_return_before_the_server_responds_and_notify_listeners_once() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/v2/chat", exchange -> {
            received.countDown();
            try {
                if (release.await(5, SECONDS)) respond(exchange, 200, RESPONSE);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });
        RecordingListener listener = new RecordingListener();
        CohereChatModel model = modelBuilder().listeners(listener).build();
        AtomicReference<Thread> caller = new AtomicReference<>();

        try {
            CompletableFuture<ChatResponse> future = executor.submit(() -> {
                        caller.set(Thread.currentThread());
                        return model.chatAsync(request());
                    })
                    .get(5, SECONDS);

            assertThat(received.await(5, SECONDS)).isTrue();
            assertThat(future).isNotDone();
            assertThat(listener.events).containsExactly("request");
            release.countDown();

            assertThat(future.get(5, SECONDS).aiMessage().text()).isEqualTo("Hello");
            assertThat(listener.events).containsExactly("request", "response");
            assertThat(listener.responseThread.get()).isNotEqualTo(caller.get());
        } finally {
            release.countDown();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_preserve_parameters_messages_and_response_metadata(boolean includeImage) throws Exception {
        LinkedBlockingQueue<String> bodies = new LinkedBlockingQueue<>();
        LinkedBlockingQueue<String> authorization = new LinkedBlockingQueue<>();
        server.createContext("/v2/chat", exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authorization.add(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, RESPONSE);
        });
        CohereChatModel model = modelBuilder()
                .defaultRequestParameters(CohereChatRequestParameters.builder()
                        .temperature(0.1)
                        .topP(0.7)
                        .maxOutputTokens(99)
                        .build())
                .build();
        UserMessage message = includeImage
                ? UserMessage.from(TextContent.from("Hello"), ImageContent.from("https://example.com/image.png"))
                : UserMessage.from("Hello");
        ChatRequest request = ChatRequest.builder()
                .messages(message)
                .parameters(CohereChatRequestParameters.builder()
                        .temperature(0.8)
                        .maxOutputTokens(12)
                        .toolSpecifications(List.of(ToolSpecification.builder()
                                .name("weather")
                                .description("Get weather")
                                .parameters(JsonObjectSchema.builder()
                                        .addStringProperty("city")
                                        .required("city")
                                        .build())
                                .build()))
                        .responseFormat(ResponseFormat.JSON)
                        .build())
                .build();

        ChatResponse synchronous = model.chat(request);
        ChatResponse asynchronous = model.chatAsync(request).get(5, SECONDS);

        Map<?, ?> syncBody = fromJson(bodies.poll(5, SECONDS), Map.class);
        Map<?, ?> asyncBody = fromJson(bodies.poll(5, SECONDS), Map.class);
        assertThat(asyncBody).isEqualTo(syncBody);
        assertThat(asyncBody.get("model")).isEqualTo("command-r");
        assertThat(asyncBody.get("temperature")).isEqualTo(0.8);
        assertThat(asyncBody.get("p")).isEqualTo(0.7);
        assertThat(asyncBody.get("max_tokens")).isEqualTo(12);
        assertThat(asyncBody.get("tools")).isNotNull();
        assertThat(asyncBody.get("response_format")).isNotNull();
        assertThat(asyncBody.get("stream")).isNull();
        if (includeImage) assertThat(asyncBody.get("messages").toString()).contains("https://example.com/image.png");
        assertThat(authorization).containsExactly("bearer test-api-key", "bearer test-api-key");
        assertThat(asynchronous).usingRecursiveComparison().isEqualTo(synchronous);
    }

    @ParameterizedTest
    @MethodSource("httpErrors")
    void should_map_http_errors(int status, Class<? extends RuntimeException> expected) {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/v2/chat", exchange -> {
            attempts.incrementAndGet();
            respond(exchange, status, "{\"message\":\"request failed\"}");
        });
        RecordingListener listener = new RecordingListener();

        CompletableFuture<ChatResponse> result =
                modelBuilder().listeners(listener).build().chatAsync(request());

        assertThatThrownBy(() -> result.get(5, SECONDS)).hasCauseInstanceOf(expected);
        assertThat(attempts).hasValue(1);
        assertThat(listener.events).containsExactly("request", "error");
    }

    static Stream<Arguments> httpErrors() {
        return Stream.of(
                Arguments.of(401, AuthenticationException.class),
                Arguments.of(403, AuthenticationException.class),
                Arguments.of(429, RateLimitException.class),
                Arguments.of(500, InternalServerException.class),
                Arguments.of(408, TimeoutException.class));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void should_not_retry_authentication_failures(int status) {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/v2/chat", exchange -> {
            attempts.incrementAndGet();
            respond(exchange, status, "{\"message\":\"authentication failed\"}");
        });

        CompletableFuture<ChatResponse> result =
                modelBuilder().maxRetries(3).build().chatAsync(request());

        assertThatThrownBy(() -> result.get(5, SECONDS)).hasCauseInstanceOf(AuthenticationException.class);
        assertThat(attempts).hasValue(1);
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 408})
    void should_retry_transient_failures_and_then_complete(int status) throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/v2/chat", exchange -> {
            if (attempts.incrementAndGet() == 1) respond(exchange, status, "{\"message\":\"retry\"}");
            else respond(exchange, 200, RESPONSE);
        });
        RecordingListener listener = new RecordingListener();

        ChatResponse response = modelBuilder()
                .maxRetries(1)
                .listeners(listener)
                .build()
                .chatAsync(request())
                .get(8, SECONDS);

        assertThat(response.aiMessage().text()).isEqualTo("Hello");
        assertThat(attempts).hasValue(2);
        assertThat(listener.events).containsExactly("request", "response");
    }

    @Test
    void should_stop_at_the_configured_retry_limit() {
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/v2/chat", exchange -> {
            attempts.incrementAndGet();
            respond(exchange, 429, "{\"message\":\"retry\"}");
        });

        CompletableFuture<ChatResponse> result =
                modelBuilder().maxRetries(2).build().chatAsync(request());

        assertThatThrownBy(() -> result.get(8, SECONDS)).hasCauseInstanceOf(RateLimitException.class);
        assertThat(attempts).hasValue(3);
    }

    @Test
    void should_complete_exceptionally_when_request_content_is_unsupported() {
        RecordingListener listener = new RecordingListener();
        ChatRequest request = ChatRequest.builder()
                .messages(UserMessage.from(AudioContent.from("https://example.com/audio.wav")))
                .build();

        CompletableFuture<ChatResponse> result =
                modelBuilder().listeners(listener).build().chatAsync(request);

        assertThatThrownBy(() -> result.get(5, SECONDS)).hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(listener.events).containsExactly("request", "error");
    }

    @Test
    void should_surface_transport_timeouts() {
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/v2/chat", exchange -> {
            try {
                release.await(5, SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });

        try {
            CompletableFuture<ChatResponse> result =
                    modelBuilder().timeout(Duration.ofMillis(500)).build().chatAsync(request());

            assertThatThrownBy(() -> result.get(5, SECONDS)).hasCauseInstanceOf(TimeoutException.class);
        } finally {
            release.countDown();
        }
    }

    @Test
    void should_not_start_another_retry_after_cancellation() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch firstFailure = new CountDownLatch(1);
        CountDownLatch anotherAttempt = new CountDownLatch(1);
        server.createContext("/v2/chat", exchange -> {
            if (attempts.incrementAndGet() > 1) anotherAttempt.countDown();
            respond(exchange, 429, "{\"message\":\"retry\"}");
            firstFailure.countDown();
        });
        RecordingListener listener = new RecordingListener();
        CompletableFuture<ChatResponse> result =
                modelBuilder().maxRetries(3).listeners(listener).build().chatAsync(request());

        try {
            assertThat(firstFailure.await(5, SECONDS)).isTrue();
            result.cancel(true);

            assertThat(result).isCancelled();
            assertThat(anotherAttempt.await(1500, MILLISECONDS)).isFalse();
            assertThat(attempts).hasValue(1);
            assertThat(listener.events).containsExactly("request");
        } finally {
            result.cancel(true);
        }
    }

    @Test
    void should_support_the_convenience_overloads() throws Exception {
        server.createContext("/v2/chat", exchange -> respond(exchange, 200, RESPONSE));
        CohereChatModel model = modelBuilder().build();

        assertThat(model.chatAsync("Hello").get(5, SECONDS)).isEqualTo("Hello");
        assertThat(model.chatAsync(UserMessage.from("Hello"))
                        .get(5, SECONDS)
                        .aiMessage()
                        .text())
                .isEqualTo("Hello");
        assertThat(model.chatAsync(List.of(UserMessage.from("Hello")))
                        .get(5, SECONDS)
                        .aiMessage()
                        .text())
                .isEqualTo("Hello");
    }

    @Test
    void should_preserve_tool_calls_and_finish_reason() throws Exception {
        String responseBody = """
                {
                  "id":"tool-response",
                  "message":{"role":"assistant","tool_calls":[
                    {"id":"call-1","type":"function","function":
                      {"name":"weather","arguments":"{\\"city\\":\\"London\\"}"}}]},
                  "finish_reason":"TOOL_CALL"
                }
                """;
        server.createContext("/v2/chat", exchange -> respond(exchange, 200, responseBody));

        ChatResponse response = modelBuilder().build().chatAsync(request()).get(5, SECONDS);

        assertThat(response.finishReason()).isEqualTo(dev.langchain4j.model.output.FinishReason.TOOL_EXECUTION);
        assertThat(response.aiMessage().toolExecutionRequests()).hasSize(1);
        assertThat(response.aiMessage().toolExecutionRequests().get(0).name()).isEqualTo("weather");
        assertThat(response.aiMessage().toolExecutionRequests().get(0).arguments())
                .isEqualTo("{\"city\":\"London\"}");
    }

    @ParameterizedTest(name = "logRequests={0}, logResponses={1}, mayInterruptIfRunning={2}")
    @CsvSource({
        "false,false,false", "false,false,true", "false,true,false", "false,true,true",
        "true,false,false", "true,false,true", "true,true,false", "true,true,true"
    })
    void should_abort_the_http_request_when_cancelled(
            boolean logRequests, boolean logResponses, boolean mayInterruptIfRunning) throws Exception {
        assertCancellationClosesConnection(logRequests, logResponses, mayInterruptIfRunning, false);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void should_propagate_cancellation_from_the_string_overload(boolean logResponses) throws Exception {
        assertCancellationClosesConnection(false, logResponses, true, true);
    }

    private void assertCancellationClosesConnection(
            boolean logRequests, boolean logResponses, boolean mayInterruptIfRunning, boolean stringOverload)
            throws Exception {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            CountDownLatch connected = new CountDownLatch(1);
            CountDownLatch disconnected = new CountDownLatch(1);
            AtomicReference<Socket> accepted = new AtomicReference<>();
            var serverTask = executor.submit(() -> {
                try (Socket peer = socket.accept()) {
                    accepted.set(peer);
                    connected.countDown();
                    byte[] buffer = new byte[1024];
                    while (peer.getInputStream().read(buffer) != -1) {
                        // Drain the request and wait for the client to close the connection.
                    }
                    disconnected.countDown();
                } catch (IOException ignored) {
                    // Closing the socket in teardown releases a failed test's server task.
                }
            });
            RecordingListener listener = new RecordingListener();
            CohereChatModel model = modelBuilder()
                    .baseUrl("http://127.0.0.1:" + socket.getLocalPort() + "/v2/")
                    .timeout(Duration.ofSeconds(30))
                    .logRequests(logRequests)
                    .logResponses(logResponses)
                    .listeners(listener)
                    .build();
            CompletableFuture<?> result = stringOverload ? model.chatAsync("Hello") : model.chatAsync(request());

            try {
                assertThat(connected.await(5, SECONDS)).isTrue();
                assertThat(result.cancel(mayInterruptIfRunning)).isTrue();
                assertThat(result).isCancelled();
                assertThat(disconnected.await(5, SECONDS))
                        .as("cancellation must close the HTTP connection")
                        .isTrue();
                serverTask.get(5, SECONDS);
                assertThat(listener.events).containsExactly("request");
            } finally {
                result.cancel(true);
                Socket peer = accepted.get();
                if (peer != null) peer.close();
                serverTask.cancel(true);
            }
        }
    }

    private static class RecordingListener implements ChatModelListener {
        final List<String> events = new CopyOnWriteArrayList<>();
        final AtomicReference<Thread> responseThread = new AtomicReference<>();

        @Override
        public void onRequest(ChatModelRequestContext context) {
            events.add("request");
        }

        @Override
        public void onResponse(ChatModelResponseContext context) {
            responseThread.set(Thread.currentThread());
            events.add("response");
        }

        @Override
        public void onError(ChatModelErrorContext context) {
            events.add("error");
        }
    }

    private CohereChatModel.Builder modelBuilder() {
        return CohereChatModel.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v2/")
                .apiKey("test-api-key")
                .modelName("command-r")
                .maxRetries(0);
    }

    private static ChatRequest request() {
        return ChatRequest.builder().messages(UserMessage.from("Hello")).build();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        } finally {
            exchange.close();
        }
    }
}
