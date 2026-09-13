package dev.langchain4j.community.model.dashscope;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.CompleteResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Verifies the reactive streaming surface
 * ({@link dev.langchain4j.model.chat.StreamingChatModel#chat(ChatRequest)})
 * against a local SSE stub of the DashScope HTTP API.
 */
class QwenStreamingChatModelReactiveTest {

    private static HttpServer server;
    private static String baseUrl;

    private static final String EVENT_TEMPLATE = "id:%d\nevent:result\n:HTTP_STATUS/200\n"
            + "data:{\"request_id\":\"stub-%d\",\"output\":{\"choices\":[{\"finish_reason\":\"%s\","
            + "\"message\":{\"role\":\"assistant\",\"content\":\"%s\"}}]},"
            + "\"usage\":{\"input_tokens\":5,\"output_tokens\":3,\"total_tokens\":8}}\n\n";

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", QwenStreamingChatModelReactiveTest::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1";
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private static void handle(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(String.format(EVENT_TEMPLATE, 1, 1, "null", "Hello").getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.write(String.format(EVENT_TEMPLATE, 2, 2, "stop", " world").getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void chat_with_publisher_should_emit_streaming_events_ending_with_complete_response() throws Exception {
        QwenStreamingChatModel model = QwenStreamingChatModel.builder()
                .apiKey("stub-api-key")
                .baseUrl(baseUrl)
                .modelName(QwenModelName.QWEN_TURBO)
                .build();

        List<ChatModelStreamingEvent> events = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> completed = new CompletableFuture<>();
        model.chat(ChatRequest.builder()
                        .messages(List.of(UserMessage.from("hi")))
                        .build())
                .subscribe(new Flow.Subscriber<>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscription.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(ChatModelStreamingEvent event) {
                        events.add(event);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        completed.completeExceptionally(throwable);
                    }

                    @Override
                    public void onComplete() {
                        completed.complete(null);
                    }
                });

        completed.get(10, SECONDS);
        assertThat(events).isNotEmpty();
        assertThat(events.get(events.size() - 1)).isInstanceOf(CompleteResponse.class);
        CompleteResponse completeResponse = (CompleteResponse) events.get(events.size() - 1);
        assertThat(completeResponse.chatResponse().aiMessage().text()).isEqualTo("Hello world");
    }

    @Test
    void chat_with_string_publisher_should_emit_partial_strings() throws Exception {
        QwenStreamingChatModel model = QwenStreamingChatModel.builder()
                .apiKey("stub-api-key")
                .baseUrl(baseUrl)
                .modelName(QwenModelName.QWEN_TURBO)
                .build();

        List<String> partials = new CopyOnWriteArrayList<>();
        CompletableFuture<Void> completed = new CompletableFuture<>();
        model.chat("hi").subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String partial) {
                partials.add(partial);
            }

            @Override
            public void onError(Throwable throwable) {
                completed.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                completed.complete(null);
            }
        });

        completed.get(10, SECONDS);
        assertThat(partials).contains("Hello");
    }
}
