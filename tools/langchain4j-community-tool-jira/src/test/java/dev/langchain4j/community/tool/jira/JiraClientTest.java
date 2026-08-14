package dev.langchain4j.community.tool.jira;

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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JiraClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static TestServer startServer(
            String path, int statusCode, String responseBody, AtomicReference<RecordedRequest> recorded)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext(path, exchange -> {
            RecordedRequest request = new RecordedRequest();
            request.method = exchange.getRequestMethod();
            request.path = exchange.getRequestURI().getPath();
            request.authorization = exchange.getRequestHeaders().getFirst("Authorization");
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

    private static List<String> toStringList(JsonNode arrayNode) {
        List<String> values = new ArrayList<>();
        if (arrayNode != null && arrayNode.isArray()) {
            arrayNode.forEach(node -> values.add(node.asText()));
        }
        return values;
    }

    @Test
    void getIssue_sendsBearerAuthAndParsesResponse() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer("/rest/api/3/issue/TEST-1", 200, "{\"key\":\"TEST-1\"}", recorded)) {
            JiraClient client = JiraClient.builder()
                    .baseUrl(server.baseUrl())
                    .authentication(JiraClient.Authentication.bearerToken("token-123"))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            JsonNode response = client.getIssue("TEST-1");

            assertThat(response.get("key").asText()).isEqualTo("TEST-1");
            RecordedRequest request = recorded.get();
            assertThat(request).isNotNull();
            assertThat(request.method).isEqualTo("GET");
            assertThat(request.path).isEqualTo("/rest/api/3/issue/TEST-1");
            assertThat(request.authorization).isEqualTo("Bearer token-123");
        }
    }

    @Test
    void searchIssues_sendsJqlAndFields() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer("/rest/api/3/search/jql", 200, "{\"issues\":[]}", recorded)) {
            JiraClient client = JiraClient.builder()
                    .baseUrl(server.baseUrl())
                    .authentication(JiraClient.Authentication.basic("user@example.com", "token"))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            JsonNode response = client.searchIssues("assignee = currentUser()", 5);

            assertThat(response.get("issues").isArray()).isTrue();
            RecordedRequest request = recorded.get();
            assertThat(request).isNotNull();
            JsonNode payload = OBJECT_MAPPER.readTree(request.body);
            assertThat(payload.get("jql").asText()).isEqualTo("assignee = currentUser()");
            assertThat(payload.get("maxResults").asInt()).isEqualTo(5);
            assertThat(toStringList(payload.get("fields")))
                    .containsExactly("summary", "status", "assignee", "priority");
            assertThat(request.method).isEqualTo("POST");
            assertThat(request.contentType).contains("application/json");
            String expectedAuth = "Basic "
                    + Base64.getEncoder().encodeToString("user@example.com:token".getBytes(StandardCharsets.UTF_8));
            assertThat(request.authorization).isEqualTo(expectedAuth);
        }
    }

    @Test
    void createIssue_postsPayload() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        String responseJson = "{\"id\":\"10000\",\"key\":\"TEST-2\"}";
        try (TestServer server = startServer("/rest/api/3/issue", 201, responseJson, recorded)) {
            JiraClient client = JiraClient.builder()
                    .baseUrl(server.baseUrl())
                    .authentication(JiraClient.Authentication.bearerToken("token-456"))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            JsonNode payload = OBJECT_MAPPER.readTree("{\"fields\":{\"summary\":\"Test\"}}");
            JsonNode response = client.createIssue(payload);

            assertThat(response.get("key").asText()).isEqualTo("TEST-2");
            RecordedRequest request = recorded.get();
            assertThat(request).isNotNull();
            JsonNode sentPayload = OBJECT_MAPPER.readTree(request.body);
            assertThat(sentPayload).isEqualTo(payload);
            assertThat(request.method).isEqualTo("POST");
            assertThat(request.path).isEqualTo("/rest/api/3/issue");
        }
    }

    @Test
    void addComment_postsPayload() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        String responseJson = "{\"id\":\"20000\"}";
        try (TestServer server = startServer("/rest/api/3/issue/TEST-3/comment", 201, responseJson, recorded)) {
            JiraClient client = JiraClient.builder()
                    .baseUrl(server.baseUrl())
                    .authentication(JiraClient.Authentication.bearerToken("token-789"))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            JsonNode payload = OBJECT_MAPPER.readTree("{\"body\":\"hello\"}");
            JsonNode response = client.addComment("TEST-3", payload);

            assertThat(response.get("id").asText()).isEqualTo("20000");
            RecordedRequest request = recorded.get();
            assertThat(request).isNotNull();
            JsonNode sentPayload = OBJECT_MAPPER.readTree(request.body);
            assertThat(sentPayload).isEqualTo(payload);
            assertThat(request.method).isEqualTo("POST");
            assertThat(request.path).isEqualTo("/rest/api/3/issue/TEST-3/comment");
        }
    }

    @Test
    void non2xxStatus_throwsJiraClientException() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        String responseJson = "{\"errorMessages\":[\"Not found\"]}";
        try (TestServer server = startServer("/rest/api/3/issue/NOPE-1", 404, responseJson, recorded)) {
            JiraClient client = JiraClient.builder()
                    .baseUrl(server.baseUrl())
                    .authentication(JiraClient.Authentication.bearerToken("token-000"))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            assertThatThrownBy(() -> client.getIssue("NOPE-1"))
                    .isInstanceOf(JiraClientException.class)
                    .extracting("statusCode", "responseBody")
                    .containsExactly(404, responseJson);
        }
    }

    @Test
    void getIssue_invalidJsonResponse_throwsJiraClientException() throws Exception {
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer("/rest/api/3/issue/TEST-JSON", 200, "not-json", recorded)) {
            JiraClient client = JiraClient.builder()
                    .baseUrl(server.baseUrl())
                    .timeout(Duration.ofSeconds(5))
                    .build();

            assertThatThrownBy(() -> client.getIssue("TEST-JSON"))
                    .isInstanceOf(JiraClientException.class)
                    .hasMessageContaining("Failed to parse JSON response from Jira")
                    .extracting("statusCode", "responseBody")
                    .containsExactly(-1, null);
        }
    }

    @Test
    void getIssue_blankKey_throwsIllegalArgumentException() {
        JiraClient client = JiraClient.builder().baseUrl("http://localhost").build();

        assertThatThrownBy(() -> client.getIssue(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issueKey cannot be null or blank");
    }

    @Test
    void searchIssues_invalidMaxResults_throwsIllegalArgumentException() {
        JiraClient client = JiraClient.builder().baseUrl("http://localhost").build();

        assertThatThrownBy(() -> client.searchIssues("project = PROJ", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxResults must be greater than 0");
    }

    @Test
    void createIssue_nullPayload_throwsNullPointerException() {
        JiraClient client = JiraClient.builder().baseUrl("http://localhost").build();

        assertThatThrownBy(() -> client.createIssue(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("payload must not be null");
    }

    @Test
    void addComment_nullBody_throwsNullPointerException() {
        JiraClient client = JiraClient.builder().baseUrl("http://localhost").build();

        assertThatThrownBy(() -> client.addComment("PROJ-1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("body must not be null");
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
        private String path;
        private String authorization;
        private String contentType;
        private String body;
    }
}
