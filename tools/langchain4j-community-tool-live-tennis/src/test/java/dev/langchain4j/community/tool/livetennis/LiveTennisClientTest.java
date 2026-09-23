package dev.langchain4j.community.tool.livetennis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
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

class LiveTennisClientTest {

    @Test
    void listLiveMatches_sendsApiKeyHeaderAndFilters() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"data\":[],\"meta\":{\"count\":0}}", recorded)) {
            LiveTennisClient client = client(server);

            JsonNode response = client.listLiveMatches("ATP", "Singles", 25);

            assertThat(response.path("data").isArray()).isTrue();
            RecordedRequest request = recorded.get();
            assertThat(request.method).isEqualTo("GET");
            assertThat(request.pathAndQuery).isEqualTo("/matches?status=live&limit=25&tour=atp&draw=singles");
            assertThat(request.apiKey).isEqualTo("test-key");
            assertThat(request.accept).isEqualTo("application/json");
            assertThat(request.userAgent).isEqualTo("langchain4j-community-tool-live-tennis");
        }
    }

    @Test
    void listLiveMatches_omitsUnsetFiltersAndDefaultsTheLimit() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"data\":[]}", recorded)) {
            LiveTennisClient client = client(server);

            client.listLiveMatches(null, "  ", null);

            assertThat(recorded.get().pathAndQuery).isEqualTo("/matches?status=live&limit=10");
        }
    }

    @Test
    void getMatch_readsOneMatchById() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"id\":187701}", recorded)) {
            LiveTennisClient client = client(server);

            assertThat(client.getMatch(187701).path("id").asLong()).isEqualTo(187701L);
            assertThat(recorded.get().pathAndQuery).isEqualTo("/matches/187701");
        }
    }

    @Test
    void listFixtures_pagesWithTourAndDrawFilters() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"data\":[]}", recorded)) {
            LiveTennisClient client = client(server);

            client.listFixtures("challenger", "doubles", 5);

            assertThat(recorded.get().pathAndQuery).isEqualTo("/fixtures?limit=5&tour=challenger&draw=doubles");
        }
    }

    @Test
    void listRankings_requiresASystemAndAcceptsAnAsOfDate() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"data\":[]}", recorded)) {
            LiveTennisClient client = client(server);

            client.listRankings("wta", "2026-09-08", 50);

            assertThat(recorded.get().pathAndQuery).isEqualTo("/rankings?system=wta&limit=50&as_of=2026-09-08");
        }
    }

    @Test
    void getHeadToHead_encodesBothPlayerNames() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"players\":null}", recorded)) {
            LiveTennisClient client = client(server);

            client.getHeadToHead("Iga Swiatek", "Aryna Sabalenka");

            assertThat(recorded.get().pathAndQuery).isEqualTo("/h2h?p1=Iga+Swiatek&p2=Aryna+Sabalenka");
        }
    }

    @Test
    void preservesTheBaseUrlPathPrefix() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"data\":[]}", recorded)) {
            LiveTennisClient client = LiveTennisClient.builder()
                    .apiKey("test-key")
                    .baseUrl(server.baseUrl() + "/api/public/v1/")
                    .timeout(Duration.ofSeconds(5))
                    .build();

            client.listLiveMatches(null, null, null);

            assertThat(recorded.get().pathAndQuery).isEqualTo("/api/public/v1/matches?status=live&limit=10");
        }
    }

    @Test
    void rejectsArgumentsTheApiWouldRefuse() {
        LiveTennisClient client = LiveTennisClient.builder()
                .apiKey("test-key")
                .baseUrl("http://localhost")
                .build();

        assertThatThrownBy(() -> client.listLiveMatches(null, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 200");
        assertThatThrownBy(() -> client.listFixtures(null, null, 201))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be between 1 and 200");
        assertThatThrownBy(() -> client.listLiveMatches("premier", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tour must be one of [atp, challenger, itf, juniors, wta]");
        assertThatThrownBy(() -> client.listLiveMatches(null, "mixed", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("draw must be one of [doubles, singles]");
        assertThatThrownBy(() -> client.getMatch(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("matchId must be a positive match id");
        assertThatThrownBy(() -> client.listRankings("utr", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("system must be one of [atp, atp_doubles, itf_jt, itf_mt, itf_wt, wta, wta_doubles]");
        assertThatThrownBy(() -> client.listRankings("atp", "last week", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("as_of must be a YYYY-MM-DD date");
        assertThatThrownBy(() -> client.getHeadToHead("Li", "Sabalenka"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("player1 must be at least 3 characters, so it can identify one player");
    }

    @Test
    void requiresApiKeyAndValidBaseUrl() {
        assertThatThrownBy(() -> LiveTennisClient.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey cannot be null or blank");
        assertThatThrownBy(() -> LiveTennisClient.builder()
                        .apiKey("test-key")
                        .baseUrl("file:///tmp/tennis")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseUrl must use HTTP or HTTPS");
        assertThatThrownBy(() -> LiveTennisClient.builder()
                        .apiKey("test-key")
                        .baseUrl("https:///api/public/v1")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseUrl must include a host");
        assertThatThrownBy(() -> LiveTennisClient.builder()
                        .apiKey("test-key")
                        .baseUrl("not a url")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseUrl is not a valid URL");
    }

    @Test
    void mapsHttpStatusesToRecoveryGuidanceWithoutExposingTheResponseBody() throws Exception {
        assertHttpError(400, "Invalid request. Check the tool arguments.");
        assertHttpError(401, "Authentication failed. Check the API key.");
        assertHttpError(403, "The API key's plan does not include this endpoint.");
        assertHttpError(404, "Requested tennis resource not found.");
        assertHttpError(410, "That match id was merged into another match record.");
        assertHttpError(429, "Rate limit or daily quota exceeded. Retry later.");
        assertHttpError(500, "Request could not be completed.");
    }

    @Test
    void carriesTheHttpStatusOnTheException() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(429, "{\"error\":\"rate_limited\",\"scope\":\"day\"}", recorded)) {
            LiveTennisClient client = client(server);

            assertThatThrownBy(() -> client.listLiveMatches(null, null, null))
                    .isInstanceOf(LiveTennisClientException.class)
                    .extracting("statusCode")
                    .isEqualTo(429);
        }
    }

    @Test
    void rejectsEmptyAndOversizedResponses() throws Exception {
        AtomicReference<RecordedRequest> emptyRecorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "", emptyRecorded)) {
            assertThatThrownBy(() -> client(server).listLiveMatches(null, null, null))
                    .isInstanceOf(LiveTennisClientException.class)
                    .hasMessage("The Live Tennis API returned an empty response.");
        }

        AtomicReference<RecordedRequest> largeRecorded = new AtomicReference<>();
        String largeResponse = "\"" + "x".repeat(5 * 1024 * 1024) + "\"";
        try (TestServer server = startServer(200, largeResponse, largeRecorded)) {
            assertThatThrownBy(() -> client(server).listLiveMatches(null, null, null))
                    .isInstanceOf(LiveTennisClientException.class)
                    .hasMessage("The Live Tennis API response exceeds the 5 MiB safety limit.");
        }
    }

    @Test
    void rejectsInvalidJson() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "<html>maintenance</html>", recorded)) {
            assertThatThrownBy(() -> client(server).listLiveMatches(null, null, null))
                    .isInstanceOf(LiveTennisClientException.class)
                    .hasMessage("The Live Tennis API returned invalid JSON.");
        }
    }

    private static void assertHttpError(int statusCode, String guidance) throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(statusCode, "{\"detail\":\"not exposed\"}", recorded)) {
            assertThatThrownBy(() -> client(server).listLiveMatches(null, null, null))
                    .isInstanceOf(LiveTennisClientException.class)
                    .hasMessage("Live Tennis API request failed: HTTP " + statusCode + ". " + guidance);
        }
    }

    private static LiveTennisClient client(TestServer server) {
        return LiveTennisClient.builder()
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
            request.apiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
            request.accept = exchange.getRequestHeaders().getFirst("Accept");
            request.userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
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
        private String accept;
        private String userAgent;
    }
}
