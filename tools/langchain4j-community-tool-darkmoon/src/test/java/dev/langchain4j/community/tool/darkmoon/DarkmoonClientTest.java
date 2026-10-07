package dev.langchain4j.community.tool.darkmoon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class DarkmoonClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String LOGIN_OK = "{\"token\":\"tok-1\",\"must_change_password\":false}";

    @Test
    void login_postsCredentialsAndReusesTheTokenAsBearer() throws Exception {
        try (TestServer server = startServer(request -> switch (request.path) {
            case "/api/v1/auth/login" -> new Reply(200, LOGIN_OK);
            default -> new Reply(200, "{\"data\":[],\"total\":0}");
        })) {
            DarkmoonClient client = client(server);

            client.listCampaigns();
            client.listCampaigns();

            assertThat(server.requests).hasSize(3);
            RecordedRequest login = server.requests.get(0);
            assertThat(login.method).isEqualTo("POST");
            assertThat(login.path).isEqualTo("/api/v1/auth/login");
            assertThat(login.authorization).isNull();
            JsonNode credentials = OBJECT_MAPPER.readTree(login.body);
            assertThat(credentials.path("username").asText()).isEqualTo("analyst");
            assertThat(credentials.path("password").asText()).isEqualTo("secret");
            assertThat(server.requests.get(1).authorization).isEqualTo("Bearer tok-1");
            assertThat(server.requests.get(2).authorization).isEqualTo("Bearer tok-1");
        }
    }

    @Test
    void listVulnerabilities_encodesTheCampaignIdInTheQuery() throws Exception {
        try (TestServer server = startServer(request -> request.path.startsWith("/api/v1/auth")
                ? new Reply(200, LOGIN_OK)
                : new Reply(200, "{\"data\":[],\"total\":0,\"stats\":{}}"))) {
            DarkmoonClient client = client(server);

            client.listVulnerabilities("camp 1&x=y");

            RecordedRequest request = server.requests.get(1);
            assertThat(request.method).isEqualTo("GET");
            assertThat(request.pathAndQuery).isEqualTo("/api/v1/vulnerabilities?campaign_id=camp+1%26x%3Dy");
        }
    }

    @Test
    void startCampaign_postsTargetAndOmitsBlankProgram() throws Exception {
        try (TestServer server = startServer(request -> request.path.startsWith("/api/v1/auth")
                ? new Reply(200, LOGIN_OK)
                : new Reply(200, "{\"run_id\":\"run_1\",\"pid\":42}"))) {
            DarkmoonClient client = client(server);

            JsonNode response = client.startCampaign("https://staging.example.com", " ");

            assertThat(response.path("run_id").asText()).isEqualTo("run_1");
            RecordedRequest request = server.requests.get(1);
            assertThat(request.path).isEqualTo("/api/v1/run/campaign");
            JsonNode body = OBJECT_MAPPER.readTree(request.body);
            assertThat(body.path("target").asText()).isEqualTo("https://staging.example.com");
            assertThat(body.has("program")).isFalse();
        }
    }

    @Test
    void startCampaign_sendsProgramWhenGiven() throws Exception {
        try (TestServer server = startServer(request -> request.path.startsWith("/api/v1/auth")
                ? new Reply(200, LOGIN_OK)
                : new Reply(200, "{\"run_id\":\"run_1\"}"))) {
            DarkmoonClient client = client(server);

            client.startCampaign("example.com", "bounty-2026");

            JsonNode body = OBJECT_MAPPER.readTree(server.requests.get(1).body);
            assertThat(body.path("program").asText()).isEqualTo("bounty-2026");
        }
    }

    @Test
    void getRunLog_encodesTheRunIdInThePath() throws Exception {
        try (TestServer server = startServer(request -> request.path.startsWith("/api/v1/auth")
                ? new Reply(200, LOGIN_OK)
                : new Reply(200, "{\"data\":[],\"total\":0}"))) {
            DarkmoonClient client = client(server);

            client.getRunLog("run 1/x");

            assertThat(server.requests.get(1).pathAndQuery).isEqualTo("/api/v1/run/logs/run%201%2Fx");
        }
    }

    @Test
    void expiredToken_isRenewedOnceAndTheCallRetried() throws Exception {
        List<String> logins = Collections.synchronizedList(new ArrayList<>());
        try (TestServer server = startServer(request -> {
            if (request.path.equals("/api/v1/auth/login")) {
                logins.add("login");
                return new Reply(200, "{\"token\":\"tok-" + logins.size() + "\"}");
            }
            return "Bearer tok-1".equals(request.authorization)
                    ? new Reply(401, "{\"detail\":\"Token expired\"}")
                    : new Reply(200, "{\"data\":[{\"id\":\"c-1\"}],\"total\":1}");
        })) {
            DarkmoonClient client = client(server);

            JsonNode response = client.listCampaigns();

            assertThat(response.path("total").asInt()).isEqualTo(1);
            assertThat(logins).hasSize(2);
        }
    }

    @Test
    void secondUnauthorized_isSurfacedInsteadOfLooping() throws Exception {
        try (TestServer server = startServer(request -> request.path.startsWith("/api/v1/auth")
                ? new Reply(200, LOGIN_OK)
                : new Reply(401, "{\"detail\":\"Token expired\"}"))) {
            DarkmoonClient client = client(server);

            assertThatThrownBy(client::listCampaigns)
                    .isInstanceOf(DarkmoonClientException.class)
                    .hasMessageContaining("HTTP 401")
                    .hasMessageContaining("Token expired");
            assertThat(server.requests).hasSize(4);
        }
    }

    @Test
    void badCredentials_surfaceTheDashboardDetail() throws Exception {
        try (TestServer server = startServer(request -> new Reply(401, "{\"detail\":\"Invalid credentials\"}"))) {
            DarkmoonClient client = client(server);

            assertThatThrownBy(client::listCampaigns)
                    .isInstanceOf(DarkmoonClientException.class)
                    .hasMessage("Darkmoon request failed: HTTP 401. Invalid credentials")
                    .satisfies(e -> assertThat(((DarkmoonClientException) e).statusCode())
                            .isEqualTo(401));
        }
    }

    @Test
    void scopeRefusal_isSurfacedVerbatim() throws Exception {
        try (TestServer server = startServer(request -> request.path.startsWith("/api/v1/auth")
                ? new Reply(200, LOGIN_OK)
                : new Reply(403, "{\"detail\":\"Target is not in the allowed scope\"}"))) {
            DarkmoonClient client = client(server);

            assertThatThrownBy(() -> client.startCampaign("example.org", null))
                    .isInstanceOf(DarkmoonClientException.class)
                    .hasMessage("Darkmoon request failed: HTTP 403. Target is not in the allowed scope");
        }
    }

    @Test
    void errorWithoutJsonBody_fallsBackToStatusGuidance() throws Exception {
        try (TestServer server = startServer(request ->
                request.path.startsWith("/api/v1/auth") ? new Reply(200, LOGIN_OK) : new Reply(404, "not found"))) {
            DarkmoonClient client = client(server);

            assertThatThrownBy(() -> client.getRunLog("run_1"))
                    .isInstanceOf(DarkmoonClientException.class)
                    .hasMessage("Darkmoon request failed: HTTP 404. Not found.");
        }
    }

    @Test
    void validationError_joinsTheArrayDetailMessages() throws Exception {
        String body = "{\"detail\":[{\"loc\":[\"body\",\"target\"],\"msg\":\"value is not a valid url\","
                + "\"type\":\"url_error\"},{\"loc\":[\"body\",\"program\"],\"msg\":\"field required\","
                + "\"type\":\"value_error.missing\"}]}";
        try (TestServer server = startServer(
                request -> request.path.startsWith("/api/v1/auth") ? new Reply(200, LOGIN_OK) : new Reply(422, body))) {
            DarkmoonClient client = client(server);

            assertThatThrownBy(() -> client.startCampaign("not-a-url", null))
                    .isInstanceOf(DarkmoonClientException.class)
                    .hasMessage("Darkmoon request failed: HTTP 422. value is not a valid url; field required");
        }
    }

    @Test
    void loginWithoutToken_isReported() throws Exception {
        try (TestServer server = startServer(request -> new Reply(200, "{\"user\":{}}"))) {
            DarkmoonClient client = client(server);

            assertThatThrownBy(client::listCampaigns)
                    .isInstanceOf(DarkmoonClientException.class)
                    .hasMessage("Darkmoon login did not return a token.");
        }
    }

    @Test
    void invalidJson_isReported() throws Exception {
        try (TestServer server = startServer(request ->
                request.path.startsWith("/api/v1/auth") ? new Reply(200, LOGIN_OK) : new Reply(200, "<html>"))) {
            DarkmoonClient client = client(server);

            assertThatThrownBy(client::listCampaigns)
                    .isInstanceOf(DarkmoonClientException.class)
                    .hasMessage("Darkmoon returned invalid JSON.");
        }
    }

    @Test
    void builder_requiresConnectionSettingsAndAnHttpUrl() {
        assertThatThrownBy(() -> DarkmoonClient.builder()
                        .baseUrl("http://localhost:8000")
                        .username("u")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
        assertThatThrownBy(() -> DarkmoonClient.builder()
                        .baseUrl("ftp://localhost")
                        .username("u")
                        .password("p")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("baseUrl must use HTTP or HTTPS");
        assertThatThrownBy(() ->
                        DarkmoonClient.builder().username("u").password("p").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("baseUrl");
    }

    @Test
    void methodsRejectBlankArguments() {
        DarkmoonClient client = DarkmoonClient.builder()
                .baseUrl("http://localhost:1")
                .username("u")
                .password("p")
                .build();

        assertThatThrownBy(() -> client.listVulnerabilities(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.startCampaign("", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.getRunLog(null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static DarkmoonClient client(TestServer server) {
        return DarkmoonClient.builder()
                .baseUrl(server.baseUrl())
                .username("analyst")
                .password("secret")
                .timeout(Duration.ofSeconds(5))
                .build();
    }

    private static TestServer startServer(Function<RecordedRequest, Reply> handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        List<RecordedRequest> requests = Collections.synchronizedList(new ArrayList<>());
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            RecordedRequest request = RecordedRequest.from(exchange);
            requests.add(request);
            Reply reply = handler.apply(request);
            byte[] responseBytes = reply.body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status, responseBytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        });
        server.start();
        return new TestServer(server, executor, requests);
    }

    private record Reply(int status, String body) {}

    private static final class RecordedRequest {
        private final String method;
        private final String path;
        private final String pathAndQuery;
        private final String authorization;
        private final String body;

        private RecordedRequest(String method, String path, String pathAndQuery, String authorization, String body) {
            this.method = method;
            this.path = path;
            this.pathAndQuery = pathAndQuery;
            this.authorization = authorization;
            this.body = body;
        }

        private static RecordedRequest from(HttpExchange exchange) throws IOException {
            return new RecordedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getRawPath()
                            + (exchange.getRequestURI().getRawQuery() == null
                                    ? ""
                                    : "?" + exchange.getRequestURI().getRawQuery()),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private record TestServer(HttpServer server, ExecutorService executor, List<RecordedRequest> requests)
            implements AutoCloseable {

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
