package dev.langchain4j.community.tool.jira;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

class JiraToolTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static ObjectNode adf(String text) {
        ObjectNode doc = OBJECT_MAPPER.createObjectNode();
        doc.put("version", 1);
        doc.put("type", "doc");
        var content = doc.putArray("content");
        ObjectNode paragraph = content.addObject();
        paragraph.put("type", "paragraph");
        var paragraphContent = paragraph.putArray("content");
        ObjectNode textNode = paragraphContent.addObject();
        textNode.put("type", "text");
        textNode.put("text", text);
        return doc;
    }

    private static ObjectNode issueNode(String key, String summary, String status, String assignee, String priority) {
        ObjectNode issue = OBJECT_MAPPER.createObjectNode();
        issue.put("key", key);
        ObjectNode fields = issue.putObject("fields");
        fields.put("summary", summary);
        fields.putObject("status").put("name", status);
        if (assignee != null) {
            fields.putObject("assignee").put("displayName", assignee);
        } else {
            fields.putNull("assignee");
        }
        fields.putObject("priority").put("name", priority);
        return issue;
    }

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

    @Test
    void getIssue_formatsDescriptionAndTruncates() throws Exception {
        String longText = "a".repeat(600);
        ObjectNode description = adf(longText);
        ObjectNode issue = OBJECT_MAPPER.createObjectNode();
        issue.put("key", "PROJ-1");
        ObjectNode fields = issue.putObject("fields");
        fields.put("summary", "Test summary");
        fields.putObject("status").put("name", "Open");
        fields.putObject("priority").put("name", "High");
        fields.putObject("assignee").put("displayName", "Alice");
        fields.set("description", description);

        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer("/rest/api/3/issue/PROJ-1", 200, issue.toString(), recorded)) {
            JiraTool tool = new JiraTool(JiraClient.builder()
                    .baseUrl(server.baseUrl())
                    .authentication(JiraClient.Authentication.bearerToken("token"))
                    .timeout(Duration.ofSeconds(5))
                    .build());

            String response = tool.getIssue("PROJ-1");

            assertThat(response)
                    .contains("[PROJ-1] Test summary (Open) | Assignee: Alice | Priority: High")
                    .contains("Description: " + "a".repeat(500) + "...");
        }
    }

    @Test
    void searchIssues_returnsListWithoutDescription() throws Exception {
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        var issues = response.putArray("issues");
        issues.add(issueNode("PROJ-1", "First", "Open", "Alice", "High"));
        issues.add(issueNode("PROJ-2", "Second", "Done", null, "Low"));

        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer("/rest/api/3/search/jql", 200, response.toString(), recorded)) {
            JiraTool tool = new JiraTool(JiraClient.builder()
                    .baseUrl(server.baseUrl())
                    .authentication(JiraClient.Authentication.bearerToken("token"))
                    .timeout(Duration.ofSeconds(5))
                    .build());

            String result = tool.searchIssues("project = PROJ");

            assertThat(result)
                    .contains("- [PROJ-1] First (Open) | Assignee: Alice | Priority: High")
                    .contains("- [PROJ-2] Second (Done) | Assignee: Unassigned | Priority: Low");
            assertThat(result).doesNotContain("Description:");
        }
    }

    @Test
    void searchIssues_returnsNoIssuesFoundWhenEmpty() throws Exception {
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.putArray("issues");

        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer("/rest/api/3/search/jql", 200, response.toString(), recorded)) {
            JiraTool tool = new JiraTool(JiraClient.builder()
                    .baseUrl(server.baseUrl())
                    .authentication(JiraClient.Authentication.bearerToken("token"))
                    .timeout(Duration.ofSeconds(5))
                    .build());

            String result = tool.searchIssues("project = PROJ");

            assertThat(result).isEqualTo("No issues found.");
        }
    }

    @Test
    void searchIssues_returnsErrorForPlainTextBody() throws Exception {
        String errorBody = "Service unavailable";
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer("/rest/api/3/search/jql", 503, errorBody, recorded)) {
            JiraTool tool = new JiraTool(JiraClient.builder()
                    .baseUrl(server.baseUrl())
                    .authentication(JiraClient.Authentication.bearerToken("token"))
                    .timeout(Duration.ofSeconds(5))
                    .build());

            String result = tool.searchIssues("project = PROJ");

            assertThat(result).isEqualTo("Error: Service unavailable");
        }
    }

    @Test
    void createIssue_buildsPayloadWithDefaultsAndAdf() throws Exception {
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.put("key", "PROJ-3");

        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer("/rest/api/3/issue", 201, response.toString(), recorded)) {
            JiraTool tool = new JiraTool(JiraClient.builder()
                    .baseUrl(server.baseUrl())
                    .authentication(JiraClient.Authentication.bearerToken("token"))
                    .timeout(Duration.ofSeconds(5))
                    .build());

            String result = tool.createIssue("PROJ", "New issue", "Hello world", null, null);

            assertThat(result).isEqualTo("Created issue [PROJ-3]");
            RecordedRequest request = recorded.get();
            JsonNode payload = OBJECT_MAPPER.readTree(request.body);
            JsonNode fields = payload.get("fields");
            assertThat(fields.get("project").get("key").asText()).isEqualTo("PROJ");
            assertThat(fields.get("summary").asText()).isEqualTo("New issue");
            assertThat(fields.get("issuetype").get("name").asText()).isEqualTo("Task");
            assertThat(fields.get("priority").get("name").asText()).isEqualTo("Medium");
            assertThat(fields.get("description").get("type").asText()).isEqualTo("doc");
            assertThat(fields.get("description")
                            .get("content")
                            .get(0)
                            .get("content")
                            .get(0)
                            .get("text")
                            .asText())
                    .isEqualTo("Hello world");
        }
    }

    @Test
    void createIssue_returnsErrorForFieldErrors() throws Exception {
        String errorJson = "{\"errors\":{\"summary\":\"Summary is required\"}}";
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer("/rest/api/3/issue", 400, errorJson, recorded)) {
            JiraTool tool = new JiraTool(JiraClient.builder()
                    .baseUrl(server.baseUrl())
                    .authentication(JiraClient.Authentication.bearerToken("token"))
                    .timeout(Duration.ofSeconds(5))
                    .build());

            String result = tool.createIssue("PROJ", "New issue", "Hello world", null, null);

            assertThat(result).isEqualTo("Error: summary: Summary is required");
        }
    }

    @Test
    void addComment_buildsAdfPayload() throws Exception {
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.put("id", "10000");

        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer("/rest/api/3/issue/PROJ-4/comment", 201, response.toString(), recorded)) {
            JiraTool tool = new JiraTool(JiraClient.builder()
                    .baseUrl(server.baseUrl())
                    .authentication(JiraClient.Authentication.bearerToken("token"))
                    .timeout(Duration.ofSeconds(5))
                    .build());

            String result = tool.addComment("PROJ-4", "Looks good");

            assertThat(result).isEqualTo("Comment added");
            RecordedRequest request = recorded.get();
            JsonNode payload = OBJECT_MAPPER.readTree(request.body);
            assertThat(payload.get("body").get("type").asText()).isEqualTo("doc");
            assertThat(payload.get("body")
                            .get("content")
                            .get(0)
                            .get("content")
                            .get(0)
                            .get("text")
                            .asText())
                    .isEqualTo("Looks good");
        }
    }

    @Test
    void addComment_returnsErrorForPlainTextBody() throws Exception {
        String errorBody = "Service unavailable";
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer("/rest/api/3/issue/PROJ-5/comment", 503, errorBody, recorded)) {
            JiraTool tool = new JiraTool(JiraClient.builder()
                    .baseUrl(server.baseUrl())
                    .authentication(JiraClient.Authentication.bearerToken("token"))
                    .timeout(Duration.ofSeconds(5))
                    .build());

            String result = tool.addComment("PROJ-5", "oops");

            assertThat(result).isEqualTo("Error: Service unavailable");
        }
    }

    @Test
    void getIssue_returnsErrorStringOnFailure() throws Exception {
        String errorJson = "{\"errorMessages\":[\"Issue not found\"]}";
        AtomicReference<RecordedRequest> recorded = new AtomicReference<>();
        try (TestServer server = startServer("/rest/api/3/issue/NOPE-1", 404, errorJson, recorded)) {
            JiraTool tool = new JiraTool(JiraClient.builder()
                    .baseUrl(server.baseUrl())
                    .authentication(JiraClient.Authentication.bearerToken("token"))
                    .timeout(Duration.ofSeconds(5))
                    .build());

            String response = tool.getIssue("NOPE-1");

            assertThat(response).isEqualTo("Error: Issue not found");
        }
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
        private String contentType;
        private String body;
    }
}
