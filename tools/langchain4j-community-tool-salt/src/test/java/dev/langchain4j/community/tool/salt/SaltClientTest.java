package dev.langchain4j.community.tool.salt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SaltClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void postCard_postsChatIdBlocksAndTextWithApiKeyHeader() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(
                200, "{\"message_id\":\"m-1\",\"resource_id\":\"c-1\",\"resource\":{\"id\":\"c-1\"}}", recorded)) {
            SaltClient client = client(server);
            JsonNode blocks = OBJECT_MAPPER.readTree("[{\"type\":\"section\",\"text\":\"Deploy now?\"}]");

            client.postCard("chat-1", blocks, "Deploy now?");

            RecordedRequest request = recorded.get();
            assertThat(request.method).isEqualTo("POST");
            assertThat(request.pathAndQuery).isEqualTo("/api/v1/cards");
            assertThat(request.apiKey).isEqualTo("test-key");
            assertThat(request.contentType).contains("application/json");
            JsonNode body = OBJECT_MAPPER.readTree(request.body);
            assertThat(body.path("chat_id").asText()).isEqualTo("chat-1");
            assertThat(body.path("text").asText()).isEqualTo("Deploy now?");
            assertThat(body.path("blocks").isArray()).isTrue();
            assertThat(body.path("blocks").get(0).path("text").asText()).isEqualTo("Deploy now?");
        }
    }

    @Test
    void postCard_omitsTextWhenNull() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{}", recorded)) {
            SaltClient client = client(server);
            JsonNode blocks = OBJECT_MAPPER.readTree("[]");

            client.postCard("chat-1", blocks, null);

            JsonNode body = OBJECT_MAPPER.readTree(recorded.get().body);
            assertThat(body.has("text")).isFalse();
        }
    }

    @Test
    void getCard_withoutAfter_omitsQueryString() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"interactions\":[]}", recorded)) {
            SaltClient client = client(server);

            client.getCard("card-1", null);

            RecordedRequest request = recorded.get();
            assertThat(request.method).isEqualTo("GET");
            assertThat(request.pathAndQuery).isEqualTo("/api/v1/cards/card-1");
        }
    }

    @Test
    void getCard_withAfter_appendsEncodedQueryParam() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"interactions\":[]}", recorded)) {
            SaltClient client = client(server);

            client.getCard("card-1", "2026-09-27T00:00:00Z");

            assertThat(recorded.get().pathAndQuery).isEqualTo("/api/v1/cards/card-1?after=2026-09-27T00%3A00%3A00Z");
        }
    }

    @Test
    void sendPlainMessage_alwaysSendsEncryptedFalse() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"message_id\":\"m-1\"}", recorded)) {
            SaltClient client = client(server);

            client.sendPlainMessage("room-1", "hello room");

            RecordedRequest request = recorded.get();
            assertThat(request.method).isEqualTo("POST");
            assertThat(request.pathAndQuery).isEqualTo("/api/v1/messages");
            JsonNode body = OBJECT_MAPPER.readTree(request.body);
            assertThat(body.path("chat_id").asText()).isEqualTo("room-1");
            assertThat(body.path("message").asText()).isEqualTo("hello room");
            // Sent explicitly and unconditionally so an ENCRYPTED chat's own
            // "explicit false claim" refusal fires cleanly (see
            // Api::V1::MessagesController#create in salt-api), rather than
            // silently storing plain text as if it were ciphertext.
            assertThat(body.path("encrypted").asBoolean()).isFalse();
        }
    }

    @Test
    void createTransferRequest_postsAllFieldsIncludingOptionalNote() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"id\":\"tr-1\",\"status\":\"pending\"}", recorded)) {
            SaltClient client = client(server);

            client.createTransferRequest("user-2", "wallet-1", "1.50", "chat-1", "Consulting fee");

            RecordedRequest request = recorded.get();
            assertThat(request.method).isEqualTo("POST");
            assertThat(request.pathAndQuery).isEqualTo("/api/v1/transfer_requests");
            JsonNode body = OBJECT_MAPPER.readTree(request.body);
            assertThat(body.path("receiver_id").asText()).isEqualTo("user-2");
            assertThat(body.path("wallet_id").asText()).isEqualTo("wallet-1");
            assertThat(body.path("amount").asText()).isEqualTo("1.50");
            assertThat(body.path("chat_id").asText()).isEqualTo("chat-1");
            assertThat(body.path("message").asText()).isEqualTo("Consulting fee");
        }
    }

    @Test
    void createTransferRequest_omitsNoteWhenNull() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"id\":\"tr-1\"}", recorded)) {
            SaltClient client = client(server);

            client.createTransferRequest("user-2", "wallet-1", "1.50", "chat-1", null);

            JsonNode body = OBJECT_MAPPER.readTree(recorded.get().body);
            assertThat(body.has("message")).isFalse();
        }
    }

    @Test
    void listChats_getsChatsEndpoint() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "[]", recorded)) {
            SaltClient client = client(server);

            JsonNode response = client.listChats();

            assertThat(recorded.get().method).isEqualTo("GET");
            assertThat(recorded.get().pathAndQuery).isEqualTo("/api/v1/chats");
            assertThat(response.isArray()).isTrue();
        }
    }

    @Test
    void everyRequestCarriesTheApiKeyHeaderNeverBearer() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "[]", recorded)) {
            client(server).listChats();

            RecordedRequest request = recorded.get();
            assertThat(request.apiKey).isEqualTo("test-key");
            assertThat(request.authorization).isNull();
            assertThat(request.userAgent).isEqualTo("langchain4j-community-tool-salt");
            assertThat(request.accept).isEqualTo("application/json");
        }
    }

    @Test
    void requiresApiKeyAndValidBaseUrl() {
        assertThatThrownBy(() -> SaltClient.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey cannot be null or blank");
        assertThatThrownBy(() -> SaltClient.builder()
                        .apiKey("test-key")
                        .baseUrl("file:///tmp/salt")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseUrl must use HTTP or HTTPS");
    }

    @Test
    void surfacesSaltsOwnErrorMessageVerbatim() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(
                422, "{\"error\":\"This room is encrypted. Messages must be sent encrypted.\"}", recorded)) {
            SaltClient client = client(server);

            assertThatThrownBy(() -> client.sendPlainMessage("chat-1", "hi"))
                    .isInstanceOf(SaltClientException.class)
                    .hasMessage("This room is encrypted. Messages must be sent encrypted.")
                    .extracting("statusCode")
                    .isEqualTo(422);
        }
    }

    @Test
    void joinsMultipleErrorsVerbatim() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server =
                startServer(422, "{\"errors\":[\"amount is invalid\",\"wallet not found\"]}", recorded)) {
            SaltClient client = client(server);

            assertThatThrownBy(() -> client.createTransferRequest("u", "w", "0", "c", null))
                    .isInstanceOf(SaltClientException.class)
                    .hasMessage("amount is invalid wallet not found");
        }
    }

    @Test
    void fallsBackToStatusSpecificGuidanceWhenBodyIsNotTheExpectedShape() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(401, "not-json-either", recorded)) {
            SaltClient client = client(server);

            assertThatThrownBy(() -> client.listChats())
                    .isInstanceOf(SaltClientException.class)
                    .hasMessage("Salt request failed: HTTP 401. Authentication failed. Check the agent's api-key.");
        }
    }

    @Test
    void rejectsEmptyAndOversizedResponses() throws Exception {
        AtomicReference<RecordedRequest> emptyRecorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "", emptyRecorded)) {
            assertThatThrownBy(() -> client(server).listChats())
                    .isInstanceOf(SaltClientException.class)
                    .hasMessage("Salt returned an empty response.");
        }

        AtomicReference<RecordedRequest> largeRecorded = new AtomicReference<>();
        String largeResponse = "\"" + "x".repeat(5 * 1024 * 1024) + "\"";
        try (TestServer server = startServer(200, largeResponse, largeRecorded)) {
            assertThatThrownBy(() -> client(server).listChats())
                    .isInstanceOf(SaltClientException.class)
                    .hasMessage("Salt response exceeds the 5 MiB safety limit.");
        }
    }

    @Test
    void rejectsInvalidJson() throws Exception {
        try (TestServer server = startServer(200, "not-json", new AtomicReference<>())) {
            assertThatThrownBy(() -> client(server).listChats())
                    .isInstanceOf(SaltClientException.class)
                    .hasMessage("Salt returned invalid JSON.");
        }
    }

    private static SaltClient client(TestServer server) {
        return SaltClient.builder()
                .apiKey("test-key")
                .baseUrl(server.baseUrl())
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    private static TestServer startServer(
            int statusCode, String responseBody, AtomicReference<RecordedRequest> recorded) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            RecordedRequest request = new RecordedRequest();
            request.method = exchange.getRequestMethod();
            request.pathAndQuery = exchange.getRequestURI().toASCIIString();
            request.apiKey = exchange.getRequestHeaders().getFirst("api-key");
            request.authorization = exchange.getRequestHeaders().getFirst("Authorization");
            request.accept = exchange.getRequestHeaders().getFirst("Accept");
            request.userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            request.contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            request.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            recorded.set(request);
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

    private static final class RecordedRequest {
        private String method;
        private String pathAndQuery;
        private String apiKey;
        private String authorization;
        private String accept;
        private String userAgent;
        private String contentType;
        private String body;
    }
}
