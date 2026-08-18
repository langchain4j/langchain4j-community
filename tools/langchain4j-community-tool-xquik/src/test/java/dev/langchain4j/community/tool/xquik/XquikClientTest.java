package dev.langchain4j.community.tool.xquik;

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

class XquikClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void searchTweets_encodesParametersAndSendsHeaders() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"tweets\":[]}", recorded)) {
            XquikClient client = client(server);

            JsonNode response = client.searchTweets("langchain4j agents", "top", 25, "next/cursor+");

            assertThat(response.path("tweets").isArray()).isTrue();
            RecordedRequest request = recorded.get();
            assertThat(request.method).isEqualTo("GET");
            assertThat(request.pathAndQuery)
                    .isEqualTo(
                            "/api/v1/x/tweets/search?q=langchain4j+agents&queryType=Top&limit=25&cursor=next%2Fcursor%2B");
            assertThat(request.apiKey).isEqualTo("test-key");
            assertThat(request.apiContract).isEqualTo("2026-04-29");
            assertThat(request.accept).isEqualTo("application/json");
            assertThat(request.userAgent).isEqualTo("langchain4j-community-tool-xquik");
        }
    }

    @Test
    void searchTweets_usesSafeDefaults() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"tweets\":[]}", recorded)) {
            XquikClient client = client(server);

            client.searchTweets("java", null, null, null);

            assertThat(recorded.get().pathAndQuery)
                    .isEqualTo("/api/v1/x/tweets/search?q=java&queryType=Latest&limit=10");
        }
    }

    @Test
    void getUserTweets_encodesUserAndOptions() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"tweets\":[]}", recorded)) {
            XquikClient client = client(server);

            client.getUserTweets("user name", true, 5, "cursor");

            assertThat(recorded.get().pathAndQuery)
                    .isEqualTo("/api/v1/x/users/user%20name/tweets?includeReplies=true&limit=5&cursor=cursor");
        }
    }

    @Test
    void checkFollow_encodesBothProfiles() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"is_following\":true}", recorded)) {
            XquikClient client = client(server);

            client.checkFollow("@source", "https://x.com/target");

            assertThat(recorded.get().pathAndQuery)
                    .isEqualTo("/api/v1/x/followers/check?source=%40source&target=https%3A%2F%2Fx.com%2Ftarget");
        }
    }

    @Test
    void downloadMedia_postsTweetInput() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server =
                startServer(200, "{\"gallery_url\":\"https://xquik.com/gallery/example\"}", recorded)) {
            XquikClient client = client(server);

            client.downloadMedia("https://x.com/user/status/123");

            RecordedRequest request = recorded.get();
            assertThat(request.method).isEqualTo("POST");
            assertThat(request.pathAndQuery).isEqualTo("/api/v1/x/media/download");
            assertThat(request.contentType).contains("application/json");
            assertThat(OBJECT_MAPPER.readTree(request.body).path("tweetInput").asText())
                    .isEqualTo("https://x.com/user/status/123");
        }
    }

    @Test
    void rejectsInvalidQueryTypeAndLimits() {
        XquikClient client = XquikClient.builder()
                .apiKey("test-key")
                .baseUrl("http://localhost")
                .build();

        assertThatThrownBy(() -> client.searchTweets("java", "popular", 10, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("queryType must be Latest or Top");
        assertThatThrownBy(() -> client.searchTweets("java", "Latest", 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 100");
        assertThatThrownBy(() -> client.getUserTweets("user", false, 101, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 100");
    }

    @Test
    void requiresApiKeyAndValidBaseUrl() {
        assertThatThrownBy(() -> XquikClient.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey cannot be null or blank");
        assertThatThrownBy(() -> XquikClient.builder()
                        .apiKey("test-key")
                        .baseUrl("file:///tmp/xquik")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseUrl must use HTTP or HTTPS");
    }

    @Test
    void reportsHttpStatusWithoutExposingResponseBody() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(402, "{\"message\":\"Credits required\"}", recorded)) {
            XquikClient client = client(server);

            assertThatThrownBy(() -> client.searchTweets("java", null, null, null))
                    .isInstanceOf(XquikClientException.class)
                    .hasMessage("Xquik request failed: HTTP 402. Credits required. Top up or subscribe first.")
                    .extracting("statusCode")
                    .isEqualTo(402);
        }
    }

    @Test
    void providesStatusSpecificRecoveryGuidance() throws Exception {
        assertHttpError(400, "Invalid request. Check the tool arguments.");
        assertHttpError(401, "Authentication failed. Check the API key.");
        assertHttpError(404, "Requested X/Twitter resource not found.");
        assertHttpError(500, "Request could not be completed.");
    }

    @Test
    void rejectsEmptyAndOversizedResponses() throws Exception {
        AtomicReference<RecordedRequest> emptyRecorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "", emptyRecorded)) {
            assertThatThrownBy(() -> client(server).searchTweets("java", null, null, null))
                    .isInstanceOf(XquikClientException.class)
                    .hasMessage("Xquik returned an empty response.");
        }

        AtomicReference<RecordedRequest> largeRecorded = new AtomicReference<>();
        String largeResponse = "\"" + "x".repeat(5 * 1024 * 1024) + "\"";
        try (TestServer server = startServer(200, largeResponse, largeRecorded)) {
            assertThatThrownBy(() -> client(server).searchTweets("java", null, null, null))
                    .isInstanceOf(XquikClientException.class)
                    .hasMessage("Xquik response exceeds the 5 MiB safety limit.");
        }
    }

    @Test
    void rejectsInvalidJson() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "not-json", recorded)) {
            XquikClient client = client(server);

            assertThatThrownBy(() -> client.searchTweets("java", null, null, null))
                    .isInstanceOf(XquikClientException.class)
                    .hasMessage("Xquik returned invalid JSON.");
        }
    }

    private static void assertHttpError(int statusCode, String guidance) throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(statusCode, "{\"message\":\"not exposed\"}", recorded)) {
            assertThatThrownBy(() -> client(server).searchTweets("java", null, null, null))
                    .isInstanceOf(XquikClientException.class)
                    .hasMessage("Xquik request failed: HTTP " + statusCode + ". " + guidance);
        }
    }

    private static XquikClient client(TestServer server) {
        return XquikClient.builder()
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
            request.apiKey = exchange.getRequestHeaders().getFirst("x-api-key");
            request.apiContract = exchange.getRequestHeaders().getFirst("xquik-api-contract");
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
        private String apiContract;
        private String accept;
        private String userAgent;
        private String contentType;
        private String body;
    }
}
