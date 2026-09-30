package dev.langchain4j.model.router;

import static dev.langchain4j.internal.CompletableFutureUtils.propagateCancellation;
import static dev.langchain4j.internal.Exceptions.unwrapCompletionException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests router delegation over real asynchronous HTTP I/O without provider credentials. */
class ModelRouterAsyncIT {
    private static final ChatRequest REQUEST =
            ChatRequest.builder().messages(UserMessage.from("ping")).build();
    private final CountDownLatch received = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final AtomicInteger unexpectedRequests = new AtomicInteger();
    private ExecutorService serverExecutor;
    private HttpServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.createContext("/delayed", exchange -> {
            received.countDown();
            try {
                if (release.await(10, TimeUnit.SECONDS)) {
                    respond(exchange, 200);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.createContext("/failure", exchange -> respond(exchange, 503));
        server.createContext("/success", exchange -> respond(exchange, 200));
        server.createContext("/unused", exchange -> {
            unexpectedRequests.incrementAndGet();
            respond(exchange, 200);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    @Test
    void shouldCompleteAsyncWithoutWaitingForHttpResponseOnCallingThread() throws Exception {
        NativeHttpModel model = model("/delayed");
        ModelRouter router = router(model);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<ChatResponse> future =
                    caller.submit(() -> router.chatAsync(REQUEST)).get(5, TimeUnit.SECONDS);
            assertTrue(received.await(5, TimeUnit.SECONDS));
            assertFalse(future.isDone());
            release.countDown();
            assertEquals("pong", future.get(5, TimeUnit.SECONDS).aiMessage().text());
        } finally {
            caller.shutdownNow();
        }
    }

    @Test
    void shouldCancelAsyncHttpFutureWithoutFailover() throws Exception {
        NativeHttpModel model = model("/delayed");
        CompletableFuture<ChatResponse> future = router(model, model("/unused")).chatAsync(REQUEST);
        assertTrue(received.await(5, TimeUnit.SECONDS));
        assertTrue(future.cancel(true));
        // The JDK HTTP future can wrap cancellation in CompletionException.
        Throwable transportError =
                model.transport.handle((response, error) -> error).get(5, TimeUnit.SECONDS);
        assertInstanceOf(CancellationException.class, unwrapCompletionException(transportError));
        assertEquals(0, unexpectedRequests.get());
    }

    @Test
    void shouldFailOverAfterAsyncHttpFailure() throws Exception {
        ChatResponse response =
                router(model("/failure"), model("/success")).chatAsync(REQUEST).get(5, TimeUnit.SECONDS);
        assertEquals("pong", response.aiMessage().text());
    }

    private static ModelRouter router(ChatModel... models) {
        return ModelRouter.builder()
                .addRoutes(models)
                .routingStrategy(new FailoverStrategy())
                .build();
    }

    private NativeHttpModel model(String path) {
        return new NativeHttpModel(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path));
    }

    private static void respond(HttpExchange exchange, int status) throws IOException {
        try (exchange) {
            byte[] body = "pong".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        }
    }

    private static final class NativeHttpModel implements ChatModel {
        private final HttpClient client = HttpClient.newHttpClient();
        private final URI uri;
        private CompletableFuture<HttpResponse<String>> transport;

        private NativeHttpModel(URI uri) {
            this.uri = uri;
        }

        @Override
        public CompletableFuture<ChatResponse> doChatAsync(ChatRequest request) {
            transport = client.sendAsync(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofString());
            CompletableFuture<ChatResponse> response = transport.thenApply(http -> {
                if (http.statusCode() != 200) {
                    throw new IllegalStateException("HTTP " + http.statusCode());
                }
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(http.body()))
                        .build();
            });
            propagateCancellation(response, transport);
            return response;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            throw new AssertionError("Router must use native async HTTP");
        }
    }
}
