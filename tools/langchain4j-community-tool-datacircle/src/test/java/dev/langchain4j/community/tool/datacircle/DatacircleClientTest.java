package dev.langchain4j.community.tool.datacircle;

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

class DatacircleClientTest {

    @Test
    void listApis_sendsNoKeyAndReadsTheArray() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "[{\"slug\":\"email-finder\"}]", recorded)) {
            JsonNode response = client(server).listApis();

            assertThat(response.isArray()).isTrue();
            RecordedRequest request = recorded.get();
            assertThat(request.method).isEqualTo("GET");
            assertThat(request.pathAndQuery).isEqualTo("/apis");
            assertThat(request.authorization).isNull();
            assertThat(request.accept).isEqualTo("application/json");
            assertThat(request.userAgent).isEqualTo("langchain4j-community-tool-datacircle");
        }
    }

    @Test
    void getBestPlans_putsTheSlugInThePathAndDefaultsTheLimit() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"plans\":[]}", recorded)) {
            DatacircleClient client = client(server);

            client.getBestPlans(" Email-Finder ", null);
            assertThat(recorded.get().pathAndQuery).isEqualTo("/apis/email-finder/best?limit=10");

            client.getBestPlans("email finder", 50);
            assertThat(recorded.get().pathAndQuery).isEqualTo("/apis/email%20finder/best?limit=50");
        }
    }

    @Test
    void searchPlans_encodesFiltersAndOmitsUnsetOnes() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"plans\":[]}", recorded)) {
            DatacircleClient client = client(server);

            client.searchPlans("Employee email", "people data & labs", "PRICE", 5, 20);
            assertThat(recorded.get().pathAndQuery)
                    .isEqualTo("/plans?sort=price&limit=5&offset=20&api=Employee+email&query=people+data+%26+labs");

            client.searchPlans(null, " ", null, null, null);
            assertThat(recorded.get().pathAndQuery).isEqualTo("/plans?sort=score&limit=10&offset=0");
        }
    }

    @Test
    void getProvider_turnsANameIntoItsSlug() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{\"apis\":[]}", recorded)) {
            client(server).getProvider("Bright Data");

            assertThat(recorded.get().pathAndQuery).isEqualTo("/providers/bright-data");
        }
    }

    @Test
    void providerSlug_followsTheApiRule() {
        assertThat(DatacircleClient.providerSlug("bright-data")).isEqualTo("bright-data");
        assertThat(DatacircleClient.providerSlug("Hunter.io")).isEqualTo("hunter-io");
        assertThat(DatacircleClient.providerSlug("AYOSMS!")).isEqualTo("ayosms");
        assertThat(DatacircleClient.providerSlug("Ad Library API (Meta)")).isEqualTo("ad-library-api-meta");
        assertThat(DatacircleClient.providerSlug("Bundesagentur für Arbeit")).isEqualTo("bundesagentur-fur-arbeit");
        assertThat(DatacircleClient.providerSlug("互亿无线 Ihuyi")).isEqualTo("ihuyi");
        assertThatThrownBy(() -> DatacircleClient.providerSlug(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider cannot be null or blank");
        assertThatThrownBy(() -> DatacircleClient.providerSlug("互亿无线"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("provider must hold letters or digits");
    }

    @Test
    void rejectsArgumentsTheApiWouldRefuseBeforeCallingIt() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "{}", recorded)) {
            DatacircleClient client = client(server);

            assertThatThrownBy(() -> client.getBestPlans("email-finder", 51))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("limit must be between 1 and 50");
            assertThatThrownBy(() -> client.searchPlans(null, null, null, 0, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("limit must be between 1 and 50");
            assertThatThrownBy(() -> client.searchPlans(null, null, null, null, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("offset must be 0 or more");
            assertThatThrownBy(() -> client.searchPlans(null, null, "speed", null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("sort must be one of [price, provider, score]");
            assertThatThrownBy(() -> client.getBestPlans(" ", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("api cannot be null or blank");
            assertThat(recorded.get()).isNull();
        }
    }

    @Test
    void requiresAValidBaseUrl() {
        assertThatThrownBy(() ->
                        DatacircleClient.builder().baseUrl("file:///tmp/v1").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseUrl must use HTTP or HTTPS");
        assertThatThrownBy(
                        () -> DatacircleClient.builder().baseUrl("https:///v1").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseUrl must include a host");
        assertThatThrownBy(() -> DatacircleClient.builder().baseUrl("not a url").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseUrl is not a valid URL");
    }

    @Test
    void passesTheApisOwnErrorSentenceOn() throws Exception {
        assertHttpError(
                404,
                "{\"error\":\"no API has the slug nope\",\"slugs\":[\"email-finder\"]}",
                "Datacircle API request failed: HTTP 404. no API has the slug nope.");
        assertHttpError(
                400,
                "{\"error\":\"limit must be 1 or more and 50 or less\"}",
                "Datacircle API request failed: HTTP 400. limit must be 1 or more and 50 or less.");
        assertHttpError(
                429,
                "{\"error\":\"too many calls\"}",
                "Datacircle API request failed: HTTP 429. too many calls. "
                        + "The API allows 60 calls a minute from one address. Retry in a minute.");
    }

    @Test
    void fallsBackToGuidanceWhenTheBodyHasNoErrorSentence() throws Exception {
        assertHttpError(400, "", "Datacircle API request failed: HTTP 400. Invalid request. Check the tool arguments.");
        assertHttpError(404, "<html>gone</html>", "Datacircle API request failed: HTTP 404. Not found.");
        assertHttpError(
                429,
                "{}",
                "Datacircle API request failed: HTTP 429. "
                        + "The API allows 60 calls a minute from one address. Retry in a minute.");
        assertHttpError(
                502, "{\"detail\":1}", "Datacircle API request failed: HTTP 502. Request could not be completed.");
    }

    @Test
    void carriesTheHttpStatusOnTheException() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(429, "{\"error\":\"too many calls\"}", recorded)) {
            assertThatThrownBy(() -> client(server).listApis())
                    .isInstanceOf(DatacircleClientException.class)
                    .extracting("statusCode")
                    .isEqualTo(429);
        }
    }

    @Test
    void rejectsEmptyAndInvalidResponses() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(200, "", recorded)) {
            assertThatThrownBy(() -> client(server).listApis())
                    .isInstanceOf(DatacircleClientException.class)
                    .hasMessage("The Datacircle API returned an empty response.");
        }
        try (TestServer server = startServer(200, "<html>maintenance</html>", recorded)) {
            assertThatThrownBy(() -> client(server).listApis())
                    .isInstanceOf(DatacircleClientException.class)
                    .hasMessage("The Datacircle API returned invalid JSON.");
        }
    }

    private static void assertHttpError(int statusCode, String body, String message) throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer(statusCode, body, recorded)) {
            assertThatThrownBy(() -> client(server).listApis())
                    .isInstanceOf(DatacircleClientException.class)
                    .hasMessage(message);
        }
    }

    private static DatacircleClient client(TestServer server) {
        return DatacircleClient.builder()
                .baseUrl(server.baseUrl() + "/")
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
            request.authorization = exchange.getRequestHeaders().getFirst("Authorization");
            request.accept = exchange.getRequestHeaders().getFirst("Accept");
            request.userAgent = exchange.getRequestHeaders().getFirst("User-Agent");
            recorded.set(request);
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, responseBytes.length == 0 ? -1 : responseBytes.length);
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
        private String authorization;
        private String accept;
        private String userAgent;
    }
}
