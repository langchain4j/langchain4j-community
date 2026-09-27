package dev.langchain4j.community.tool.salt;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SaltToolTest {

    @Test
    void postCard_returnsCardAndMessageIds() throws Exception {
        String response = """
                {"message_id":"m-1","resource_type":"Card","resource_id":"c-1","resource":{"id":"c-1"}}
                """;
        try (TestServer server = startServer(200, response, new AtomicReference<>())) {
            SaltTool tool = tool(server);

            String result = tool.postCard("chat-1", "Deploy now?", List.of("Yes", "No"));

            assertThat(result).contains("cardId=c-1").contains("messageId=m-1").contains("readCardTaps");
        }
    }

    @Test
    void postCard_buildsSectionAndActionsBlocksWithSlugifiedActionIds() throws Exception {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        try (TestServer server =
                startServer(200, "{\"message_id\":\"m-1\",\"resource\":{\"id\":\"c-1\"}}", capturedBody)) {
            SaltTool tool = tool(server);

            tool.postCard("chat-1", "Ship it?", List.of("Yes please!", "No thanks"));

            String body = capturedBody.get();
            assertThat(body).contains("\"type\":\"section\"");
            assertThat(body).contains("\"text\":\"Ship it?\"");
            assertThat(body).contains("\"type\":\"actions\"");
            assertThat(body).contains("\"action_id\":\"yes_please\"");
            assertThat(body).contains("\"action_id\":\"no_thanks\"");
            assertThat(body).contains("\"label\":\"Yes please!\"");
        }
    }

    @Test
    void postCard_rejectsTooFewOrTooManyButtons() {
        SaltTool tool = SaltTool.builder()
                .apiKey("test-key")
                .baseUrl("http://localhost:1")
                .build();

        assertThat(tool.postCard("chat-1", "Q", List.of())).contains("Error:").contains("1 to 5");
        assertThat(tool.postCard("chat-1", "Q", List.of("1", "2", "3", "4", "5", "6")))
                .contains("Error:")
                .contains("1 to 5");
    }

    @Test
    void postCard_rejectsBlankQuestion() {
        SaltTool tool = SaltTool.builder()
                .apiKey("test-key")
                .baseUrl("http://localhost:1")
                .build();

        assertThat(tool.postCard("chat-1", "  ", List.of("Yes"))).isEqualTo("Error: question must not be blank.");
    }

    @Test
    void postCard_rejectsOverlongButtonLabel() {
        SaltTool tool = SaltTool.builder()
                .apiKey("test-key")
                .baseUrl("http://localhost:1")
                .build();

        String tooLong = "x".repeat(41);
        assertThat(tool.postCard("chat-1", "Q", List.of(tooLong)))
                .contains("Error:")
                .contains("40 characters");
    }

    @Test
    void readCardTaps_formatsEachTapNewestFirst() throws Exception {
        String response = """
                {"id":"c-1","state":{},"owner_id":"o-1","interactions":[
                  {"id":"i-2","user_id":"u-1","action_id":"yes","value":null,"created_at":"2026-09-27T00:00:01Z"},
                  {"id":"i-1","user_id":"u-1","action_id":"pay","value":{"transfer_request_id":"tr-1"},
                   "created_at":"2026-09-27T00:00:00Z","transfer_request_id":"tr-1","transfer_request_status":"completed"}
                ]}
                """;
        try (TestServer server = startServer(200, response, new AtomicReference<>())) {
            SaltTool tool = tool(server);

            String result = tool.readCardTaps("c-1", null);

            assertThat(result)
                    .startsWith("Taps on card c-1 (newest first):")
                    .contains("action=yes user_id=u-1")
                    .contains("action=pay user_id=u-1")
                    .contains("transfer_request_id=tr-1 transfer_request_status=completed");
        }
    }

    @Test
    void readCardTaps_reportsNoTapsYet() throws Exception {
        try (TestServer server = startServer(200, "{\"id\":\"c-1\",\"interactions\":[]}", new AtomicReference<>())) {
            SaltTool tool = tool(server);

            assertThat(tool.readCardTaps("c-1", null)).isEqualTo("No taps yet on card c-1.");
        }
    }

    @Test
    void readCardTaps_surfacesNotFoundForUnownedOrUnknownCard() throws Exception {
        try (TestServer server = startServer(404, "{\"error\":\"Not found\"}", new AtomicReference<>())) {
            SaltTool tool = tool(server);

            assertThat(tool.readCardTaps("c-1", null)).isEqualTo("Error: Not found");
        }
    }

    @Test
    void sendMessage_confirmsMessageId() throws Exception {
        try (TestServer server = startServer(200, "{\"message_id\":\"m-1\"}", new AtomicReference<>())) {
            SaltTool tool = tool(server);

            assertThat(tool.sendMessage("room-1", "hello")).isEqualTo("Message sent. message_id=m-1");
        }
    }

    @Test
    void sendMessage_surfacesSaltsEncryptedChatRefusalVerbatim() throws Exception {
        try (TestServer server = startServer(
                422,
                "{\"error\":\"This room is encrypted. Messages must be sent encrypted.\"}",
                new AtomicReference<>())) {
            SaltTool tool = tool(server);

            assertThat(tool.sendMessage("chat-1", "hello"))
                    .isEqualTo("Error: This room is encrypted. Messages must be sent encrypted.");
        }
    }

    @Test
    void createPaymentRequest_confirmsIdAndStatus() throws Exception {
        try (TestServer server =
                startServer(200, "{\"id\":\"tr-1\",\"status\":\"pending\"}", new AtomicReference<>())) {
            SaltTool tool = tool(server);

            String result = tool.createPaymentRequest("chat-1", "user-2", "wallet-1", "1.50", "Consulting fee");

            assertThat(result).isEqualTo("Payment request created. id=tr-1 status=pending");
        }
    }

    @Test
    void listChats_formatsEachChatOneLine() throws Exception {
        String response = """
                [
                  {"session":{"id":"chat-1","name":"Deploys","encrypted":false,
                    "users":[{"id":"u-1"},{"id":"u-2"}],"unread_count":3}},
                  {"session":{"id":"chat-2","name":"","encrypted":true,"users":[{"id":"u-1"}],"unread_count":0}}
                ]
                """;
        try (TestServer server = startServer(200, response, new AtomicReference<>())) {
            SaltTool tool = tool(server);

            String result = tool.listChats();

            assertThat(result)
                    .contains("id=chat-1 name=Deploys encrypted=false members=2 unread=3")
                    .contains("id=chat-2 name=(unnamed) encrypted=true members=1 unread=0");
        }
    }

    @Test
    void listChats_reportsNoChatsYet() throws Exception {
        try (TestServer server = startServer(200, "[]", new AtomicReference<>())) {
            SaltTool tool = tool(server);

            assertThat(tool.listChats()).isEqualTo("No chats yet.");
        }
    }

    private static SaltTool tool(TestServer server) {
        return SaltTool.builder()
                .apiKey("test-key")
                .baseUrl(server.baseUrl())
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    private static TestServer startServer(int statusCode, String responseBody, AtomicReference<String> capturedBody)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        });
        server.start();
        return new TestServer(server, executor);
    }

    private record TestServer(HttpServer server, ExecutorService executor) implements AutoCloseable {

        private String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }
}
