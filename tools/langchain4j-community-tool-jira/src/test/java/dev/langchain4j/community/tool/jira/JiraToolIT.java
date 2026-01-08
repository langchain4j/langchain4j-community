package dev.langchain4j.community.tool.jira;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.tool.ToolExecution;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class JiraToolIT {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(JiraToolIT.class);

    private final OpenAiChatModel model = buildModel(false);
    private final OpenAiChatModel strictToolsModel = buildModel(true);

    private TestServer server;

    interface Assistant {
        Result<String> chat(String userMessage);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Test
    void should_get_issue_via_tool() throws IOException {
        String issueKey = "PROJ-101";
        String summary = "Summary-" + UUID.randomUUID();
        String status = "Open";
        String assignee = "Alice";
        String priority = "High";
        String description = "Description-" + UUID.randomUUID();
        ObjectNode issue = issueNode(issueKey, summary, status, assignee, priority, description);

        server = startServer(new Route("/rest/api/3/issue/" + issueKey, 200, issue.toString()));
        JiraTool tool = jiraTool(server.baseUrl());

        Result<String> result = assistant(tool, true)
                .chat("Use the Jira tool to fetch issue " + issueKey + " and return the tool output only.");

        ToolExecution execution = toolExecutionByName(result, "getIssue");
        assertThat(execution.request().arguments()).contains(issueKey);
        assertThat(execution.result()).contains(issueKey, summary, status, assignee, priority, "Description:");
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_handle_non_ascii_issue_text() throws IOException {
        String issueKey = "PROJ-UNICODE";
        String summary = "\u4e2d\u6587-\u6d4b\u8bd5-" + UUID.randomUUID();
        String description = "\u65e5\u672c\u8a9e-\u30c6\u30b9\u30c8-" + UUID.randomUUID();
        ObjectNode issue = issueNode(issueKey, summary, "Open", "Alice", "High", description);

        server = startServer(new Route("/rest/api/3/issue/" + issueKey, 200, issue.toString()));
        JiraTool tool = jiraTool(server.baseUrl());

        Result<String> result = assistant(tool, true)
                .chat("Use the Jira tool to fetch issue " + issueKey + " and return the tool output only.");

        ToolExecution execution = toolExecutionByName(result, "getIssue");
        assertThat(execution.result()).contains(summary, description);
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_search_issues_with_jql() throws IOException {
        String jql = "project = PROJ ORDER BY created DESC";
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        var issues = response.putArray("issues");
        issues.add(issueNode("PROJ-1", "First", "Open", "Alice", "High", ""));
        issues.add(issueNode("PROJ-2", "Second", "Done", "Bob", "Low", ""));

        server = startServer(new Route("/rest/api/3/search/jql", 200, response.toString()));
        JiraTool tool = jiraTool(server.baseUrl());

        Result<String> result = assistant(tool, true)
                .chat("Use the Jira search tool with JQL: \"" + jql + "\". Return the issue keys.");

        ToolExecution execution = toolExecutionByName(result, "searchIssues");
        assertThat(execution.request().arguments()).contains(jql);
        assertThat(execution.result()).contains("PROJ-1", "PROJ-2");
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_return_no_issues_for_empty_search_results() throws IOException {
        String jql = "project = PROJ AND status = Done";
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        response.putArray("issues");

        server = startServer(new Route("/rest/api/3/search/jql", 200, response.toString()));
        JiraTool tool = jiraTool(server.baseUrl());

        Result<String> result = assistant(tool, true)
                .chat("Use the Jira search tool with JQL: \"" + jql + "\". Return the tool output only.");

        ToolExecution execution = toolExecutionByName(result, "searchIssues");
        assertThat(execution.result()).isEqualTo("No issues found.");
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_request_max_results_for_search_pagination() throws IOException {
        String jql = "project = PROJ ORDER BY created DESC";
        ObjectNode response = OBJECT_MAPPER.createObjectNode();
        var issues = response.putArray("issues");
        issues.add(issueNode("PROJ-7", "Paged", "Open", "Alice", "High", ""));

        server = startServer(new Route("/rest/api/3/search/jql", 200, response.toString()));
        JiraTool tool = jiraTool(server.baseUrl());

        Result<String> result = assistant(tool, true)
                .chat("Use the Jira search tool with JQL: \"" + jql + "\". Return the tool output only.");

        ToolExecution execution = toolExecutionByName(result, "searchIssues");
        assertThat(execution.result()).contains("PROJ-7");
        RecordedRequest request = server.recordedRequest("/rest/api/3/search/jql");
        JsonNode payload = OBJECT_MAPPER.readTree(request.body);
        assertThat(payload.get("maxResults").asInt()).isEqualTo(5);
        assertThat(payload.get("jql").asText()).isEqualTo(jql);
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_create_issue_with_fields() throws IOException {
        String projectKey = "PROJ";
        String summary = "Summary-" + UUID.randomUUID();
        String description = "Description-" + UUID.randomUUID();
        String issueType = "Bug";
        String priority = "High";
        ObjectNode response = OBJECT_MAPPER.createObjectNode().put("key", "PROJ-99");

        server = startServer(new Route("/rest/api/3/issue", 201, response.toString()));
        JiraTool tool = jiraTool(server.baseUrl());

        Result<String> result = assistant(tool, true)
                .chat("Create a Jira issue in project " + projectKey + " with summary \"" + summary
                        + "\", description \"" + description + "\", issue type " + issueType
                        + ", and priority " + priority + ". Return the tool output only.");

        ToolExecution execution = toolExecutionByName(result, "createIssue");
        assertThat(execution.result()).contains("Created issue [PROJ-99]");
        RecordedRequest request = server.recordedRequest("/rest/api/3/issue");
        JsonNode payload = OBJECT_MAPPER.readTree(request.body);
        JsonNode fields = payload.get("fields");
        assertThat(fields.get("project").get("key").asText()).isEqualTo(projectKey);
        assertThat(fields.get("summary").asText()).isEqualTo(summary);
        assertThat(fields.get("issuetype").get("name").asText()).isEqualTo(issueType);
        assertThat(fields.get("priority").get("name").asText()).isEqualTo(priority);
        assertThat(fields.get("description")
                        .get("content")
                        .get(0)
                        .get("content")
                        .get(0)
                        .get("text")
                        .asText())
                .isEqualTo(description);
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_return_error_for_create_issue_failure() throws IOException {
        String projectKey = "PROJ";
        String summary = "Summary-" + UUID.randomUUID();
        String description = "Description-" + UUID.randomUUID();
        String errorBody = "Service unavailable";

        server = startServer(new Route("/rest/api/3/issue", 503, errorBody));
        JiraTool tool = jiraTool(server.baseUrl());

        Result<String> result = assistant(tool, false, 2)
                .chat("Call the Jira create issue tool exactly once using project " + projectKey
                        + ", summary \"" + summary + "\", description \"" + description
                        + "\", issue type Bug, and priority High. Do not retry. "
                        + "Return the tool output only.");

        ToolExecution execution = toolExecutionByName(result, "createIssue");
        assertThat(execution.result()).contains("Error: Service unavailable");
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_add_comment_with_adf() throws IOException {
        String issueKey = "PROJ-7";
        String comment = "Comment-" + UUID.randomUUID();
        ObjectNode response = OBJECT_MAPPER.createObjectNode().put("id", "10000");

        server = startServer(new Route("/rest/api/3/issue/" + issueKey + "/comment", 201, response.toString()));
        JiraTool tool = jiraTool(server.baseUrl());

        Result<String> result = assistant(tool, true)
                .chat("Add a comment to issue " + issueKey + " with text \"" + comment
                        + "\". Return the tool output only.");

        ToolExecution execution = toolExecutionByName(result, "addComment");
        assertThat(execution.result()).isEqualTo("Comment added");
        RecordedRequest request = server.recordedRequest("/rest/api/3/issue/" + issueKey + "/comment");
        JsonNode payload = OBJECT_MAPPER.readTree(request.body);
        assertThat(payload.get("body").get("type").asText()).isEqualTo("doc");
        assertThat(payload.get("body")
                        .get("content")
                        .get(0)
                        .get("content")
                        .get(0)
                        .get("text")
                        .asText())
                .isEqualTo(comment);
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_return_error_for_comment_failure() throws IOException {
        String issueKey = "PROJ-8";
        String comment = "Comment-" + UUID.randomUUID();
        String errorBody = "Service unavailable";

        server = startServer(new Route("/rest/api/3/issue/" + issueKey + "/comment", 503, errorBody));
        JiraTool tool = jiraTool(server.baseUrl());

        Result<String> result = assistant(tool, false, 2)
                .chat("Call the Jira add comment tool for issue " + issueKey + " with text \"" + comment
                        + "\" exactly once. Do not retry. Return the tool output only.");

        ToolExecution execution = toolExecutionByName(result, "addComment");
        assertThat(execution.result()).contains("Error: Service unavailable");
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_return_error_for_missing_issue() throws IOException {
        String issueKey = "PROJ-404";
        String errorBody = "{\"errorMessages\":[\"Issue not found\"]}";

        server = startServer(new Route("/rest/api/3/issue/" + issueKey, 404, errorBody));
        JiraTool tool = jiraTool(server.baseUrl());

        Result<String> result = assistant(tool, false, 2)
                .chat("Call the Jira tool to fetch issue " + issueKey
                        + " exactly once. Do not retry. Return the tool output only.");

        ToolExecution execution = toolExecutionByName(result, "getIssue");
        assertThat(execution.result()).contains("Error: Issue not found");
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_return_error_for_invalid_jql() throws IOException {
        String jql = "project = PROJ AND ???";
        String errorBody = "{\"errorMessages\":[\"JQL is invalid\"]}";

        server = startServer(new Route("/rest/api/3/search/jql", 400, errorBody));
        JiraTool tool = jiraTool(server.baseUrl());

        Result<String> result = assistant(tool, false, 2)
                .chat("Call the Jira search tool with JQL: \"" + jql
                        + "\" exactly once. Do not retry. Return the tool output only.");

        ToolExecution execution = toolExecutionByName(result, "searchIssues");
        assertThat(execution.result()).contains("Error: JQL is invalid");
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_return_error_for_unauthorized() throws IOException {
        String issueKey = "PROJ-401";
        String errorBody = "{\"errorMessages\":[\"Unauthorized\"]}";

        server = startServer(new Route("/rest/api/3/issue/" + issueKey, 401, errorBody));
        JiraTool tool = jiraTool(server.baseUrl());

        Result<String> result = assistant(tool, false, 2)
                .chat("Call the Jira tool to fetch issue " + issueKey
                        + " exactly once. Do not retry. Return the tool output only.");

        ToolExecution execution = toolExecutionByName(result, "getIssue");
        assertThat(execution.result()).contains("Error: Unauthorized");
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_return_error_for_forbidden() throws IOException {
        String issueKey = "PROJ-403";
        String errorBody = "{\"errorMessages\":[\"Forbidden\"]}";

        server = startServer(new Route("/rest/api/3/issue/" + issueKey, 403, errorBody));
        JiraTool tool = jiraTool(server.baseUrl());

        Result<String> result = assistant(tool, false, 2)
                .chat("Call the Jira tool to fetch issue " + issueKey
                        + " exactly once. Do not retry. Return the tool output only.");

        ToolExecution execution = toolExecutionByName(result, "getIssue");
        assertThat(execution.result()).contains("Error: Forbidden");
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_return_error_for_rate_limited() throws IOException {
        String jql = "project = PROJ";
        String errorBody = "{\"errorMessages\":[\"Rate limit exceeded\"]}";

        server = startServer(new Route("/rest/api/3/search/jql", 429, errorBody));
        JiraTool tool = jiraTool(server.baseUrl());

        Result<String> result = assistant(tool, false, 2)
                .chat("Call the Jira search tool with JQL: \"" + jql
                        + "\" exactly once. Do not retry. Return the tool output only.");

        ToolExecution execution = toolExecutionByName(result, "searchIssues");
        assertThat(execution.result()).contains("Error: Rate limit exceeded");
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_return_error_for_timeout_without_retry() throws IOException {
        String issueKey = "PROJ-408";
        String path = "/rest/api/3/issue/" + issueKey;

        server = startServer(new Route(path, 200, "{}", 1000));
        JiraTool tool = jiraTool(server.baseUrl(), Duration.ofMillis(200));

        Result<String> result = assistant(tool, false, 2)
                .chat("Call the Jira tool to fetch issue " + issueKey
                        + " exactly once. The request will time out. Do not retry. "
                        + "Return the tool output only.");

        ToolExecution execution = toolExecutionByName(result, "getIssue");
        assertThat(execution.result()).startsWith("Error:");
        assertThat(server.requestCount(path)).isEqualTo(1);
        assertThat(result.content()).isNotBlank();
        logger.info(result.content());
    }

    @Test
    void should_not_call_tool_for_unrelated_question() {
        server = startServerUnchecked(new Route("/noop", 200, "{}"));
        JiraTool tool = jiraTool(server.baseUrl());

        Result<String> result =
                assistant(tool, false).chat("Do not call any tools. Answer only with the number: 2 + 2 = ?");

        assertThat(result.toolExecutions()).isEmpty();
        assertThat(result.content()).contains("4");
        logger.info(result.content());
    }

    private Assistant assistant(JiraTool tool, boolean strictTools) {
        return assistant(tool, strictTools, null);
    }

    private Assistant assistant(JiraTool tool, boolean strictTools, Integer maxSequentialToolsInvocations) {
        AiServices<Assistant> builder = AiServices.builder(Assistant.class)
                .chatModel(strictTools ? strictToolsModel : model)
                .tools(tool)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10));
        if (maxSequentialToolsInvocations != null) {
            builder.maxSequentialToolsInvocations(maxSequentialToolsInvocations);
        }
        return builder.build();
    }

    private JiraTool jiraTool(String baseUrl) {
        return jiraTool(baseUrl, Duration.ofSeconds(10));
    }

    private JiraTool jiraTool(String baseUrl, Duration timeout) {
        return JiraTool.builder()
                .baseUrl(baseUrl)
                .authentication(JiraClient.Authentication.bearerToken("token"))
                .timeout(timeout)
                .build();
    }

    private ToolExecution toolExecutionByName(Result<String> result, String name) {
        assertThat(result.toolExecutions()).isNotEmpty();
        return result.toolExecutions().stream()
                .filter(execution -> name.equals(execution.request().name()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No tool execution for " + name));
    }

    private static OpenAiChatModel buildModel(boolean strictTools) {
        return OpenAiChatModel.builder()
                .baseUrl(System.getenv("OPENAI_BASE_URL"))
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .organizationId(System.getenv("OPENAI_ORGANIZATION_ID"))
                .modelName(GPT_4_O_MINI)
                .temperature(0.0)
                .strictTools(strictTools)
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    private static ObjectNode issueNode(
            String key, String summary, String status, String assignee, String priority, String description) {
        ObjectNode issue = OBJECT_MAPPER.createObjectNode();
        issue.put("key", key);
        ObjectNode fields = issue.putObject("fields");
        fields.put("summary", summary);
        fields.putObject("status").put("name", status);
        fields.putObject("priority").put("name", priority);
        if (assignee == null) {
            fields.putNull("assignee");
        } else {
            fields.putObject("assignee").put("displayName", assignee);
        }
        fields.set("description", JiraUtils.toADF(description));
        return issue;
    }

    private TestServer startServer(Route... routes) throws IOException {
        return new TestServer(routes);
    }

    private TestServer startServerUnchecked(Route... routes) {
        try {
            return startServer(routes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start test server", e);
        }
    }

    private static final class Route {
        private final String path;
        private final int status;
        private final String body;
        private final long delayMillis;

        private Route(String path, int status, String body) {
            this(path, status, body, 0L);
        }

        private Route(String path, int status, String body, long delayMillis) {
            this.path = path;
            this.status = status;
            this.body = body;
            this.delayMillis = delayMillis;
        }
    }

    private static final class TestServer implements AutoCloseable {
        private final HttpServer server;
        private final ScheduledExecutorService executor;
        private final Map<String, RecordedRequest> recordedRequests = new ConcurrentHashMap<>();
        private final Map<String, Integer> requestCounts = new ConcurrentHashMap<>();

        private TestServer(Route... routes) throws IOException {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            executor = Executors.newSingleThreadScheduledExecutor();
            server.setExecutor(executor);
            for (Route route : routes) {
                server.createContext(route.path, exchange -> {
                    RecordedRequest request = new RecordedRequest();
                    request.path = exchange.getRequestURI().getPath();
                    request.body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    recordedRequests.put(request.path, request);
                    requestCounts.merge(request.path, 1, Integer::sum);
                    if (route.delayMillis > 0) {
                        executor.schedule(
                                () -> writeResponse(exchange, route), route.delayMillis, TimeUnit.MILLISECONDS);
                        return;
                    }
                    writeResponse(exchange, route);
                });
            }
            server.start();
        }

        private static void writeResponse(HttpExchange exchange, Route route) {
            byte[] responseBytes = route.body.getBytes(StandardCharsets.UTF_8);
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(route.status, responseBytes.length);
                try (OutputStream outputStream = exchange.getResponseBody()) {
                    outputStream.write(responseBytes);
                }
            } catch (IOException ignored) {
                // Client may have timed out while we were writing the response.
            }
        }

        private String baseUrl() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        private RecordedRequest recordedRequest(String path) {
            RecordedRequest request = recordedRequests.get(path);
            if (request == null) {
                throw new IllegalStateException("No request recorded for " + path);
            }
            return request;
        }

        private int requestCount(String path) {
            return requestCounts.getOrDefault(path, 0);
        }

        @Override
        public void close() {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private static final class RecordedRequest {
        private String path;
        private String body;
    }
}
