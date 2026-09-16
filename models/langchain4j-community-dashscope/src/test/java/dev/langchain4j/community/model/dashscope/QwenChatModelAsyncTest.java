package dev.langchain4j.community.model.dashscope;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alibaba.dashscope.exception.ApiException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Exercises the async chat surface ({@link dev.langchain4j.model.chat.ChatModel#chatAsync})
 * against a local stub of the DashScope HTTP API, so non-blocking behavior is verified
 * without real credentials.
 */
class QwenChatModelAsyncTest {

    private static HttpServer server;
    private static String baseUrl;
    private static final List<String> requestedPaths = new CopyOnWriteArrayList<>();
    private static volatile String responseBody;
    private static volatile int responseStatus = 200;
    private static volatile long responseDelayMillis = 0;

    private static final String GENERATION_JSON = "{\"request_id\":\"stub-1\","
            + "\"output\":{\"choices\":[{\"finish_reason\":\"stop\","
            + "\"message\":{\"role\":\"assistant\",\"content\":\"hello from stub\"}}]},"
            + "\"usage\":{\"input_tokens\":11,\"output_tokens\":3,\"total_tokens\":14}}";

    private static final String MULTIMODAL_JSON = "{\"request_id\":\"stub-2\","
            + "\"output\":{\"choices\":[{\"finish_reason\":\"stop\","
            + "\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"mm from stub\"}]}}]},"
            + "\"usage\":{\"input_tokens\":7,\"output_tokens\":2,\"total_tokens\":9}}";

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", QwenChatModelAsyncTest::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1";
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    @BeforeEach
    void resetStub() {
        requestedPaths.clear();
        responseBody = GENERATION_JSON;
        responseStatus = 200;
        responseDelayMillis = 0;
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requestedPaths.add(path);
        exchange.getRequestBody().readAllBytes();
        if (responseDelayMillis > 0) {
            try {
                Thread.sleep(responseDelayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(responseStatus, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static QwenChatModel model(String modelName) {
        return QwenChatModel.builder()
                .apiKey("stub-api-key")
                .baseUrl(baseUrl)
                .modelName(modelName)
                .build();
    }

    @Test
    void synchronous_chat_is_stubbed_correctly() {
        ChatResponse response = model(QwenModelName.QWEN_TURBO)
                .chat(ChatRequest.builder()
                        .messages(List.of(UserMessage.from("hi")))
                        .build());

        assertThat(response.aiMessage().text()).isEqualTo("hello from stub");
        assertThat(requestedPaths).anyMatch(path -> path.endsWith("/services/aigc/text-generation/generation"));
    }

    @Test
    void chatAsync_should_not_block_caller_and_complete_with_parsed_response() throws Exception {
        responseDelayMillis = 300;

        CompletableFuture<ChatResponse> future = model(QwenModelName.QWEN_TURBO)
                .chatAsync(ChatRequest.builder()
                        .messages(List.of(UserMessage.from("hi")))
                        .build());

        assertThat(future.isDone()).as("dispatch must be non-blocking").isFalse();

        ChatResponse response = future.get(10, SECONDS);
        assertThat(response.aiMessage().text()).isEqualTo("hello from stub");
        assertThat(response.tokenUsage().inputTokenCount()).isEqualTo(11);
        assertThat(response.tokenUsage().outputTokenCount()).isEqualTo(3);
        assertThat(requestedPaths).anyMatch(path -> path.endsWith("/services/aigc/text-generation/generation"));
    }

    @Test
    void chatAsync_should_complete_exceptionally_on_error_response() {
        responseStatus = 401;
        responseBody = "{\"request_id\":\"stub-3\",\"code\":\"InvalidApiKey\",\"message\":\"bad key\"}";

        CompletableFuture<ChatResponse> future = model(QwenModelName.QWEN_TURBO)
                .chatAsync(ChatRequest.builder()
                        .messages(List.of(UserMessage.from("hi")))
                        .build());

        assertThatThrownBy(() -> future.get(10, SECONDS)).hasRootCauseInstanceOf(ApiException.class);
    }

    @Test
    void chatAsync_should_use_multimodal_endpoint_for_multimodal_models() throws Exception {
        responseBody = MULTIMODAL_JSON;

        ChatResponse response = model(QwenModelName.QWEN_VL_PLUS)
                .chatAsync(ChatRequest.builder()
                        .messages(List.of(UserMessage.from("hi")))
                        .build())
                .get(10, SECONDS);

        assertThat(response.aiMessage().text()).isEqualTo("mm from stub");
        assertThat(requestedPaths).anyMatch(path -> path.endsWith("/services/aigc/multimodal-generation/generation"));
    }
}
