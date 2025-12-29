package dev.langchain4j.community.tool.jira;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.http.client.HttpClient;
import java.time.Duration;
import java.util.Objects;

/**
 * Agent-ready Jira tool backed by {@link JiraClient}.
 */
public final class JiraTool {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_MAX_RESULTS = 5;

    private final JiraClient client;

    public JiraTool(JiraClient client) {
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    /**
     * Creates a new builder for {@link JiraTool}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Tool("Get Jira issue details by key.")
    public String getIssue(@P("Issue key, e.g. PROJ-123") String key) {
        try {
            JsonNode issue = client.getIssue(key);
            String issueKey = JiraUtils.textOrDefault(issue.get("key"), "(unknown key)");
            JsonNode fields = issue.get("fields");
            String summary = JiraUtils.textOrDefault(JiraUtils.getNode(fields, "summary"), "(no summary)");
            String status = JiraUtils.textOrDefault(JiraUtils.getNode(fields, "status", "name"), "Unknown");
            String priority = JiraUtils.textOrDefault(JiraUtils.getNode(fields, "priority", "name"), "Unspecified");
            String assignee =
                    JiraUtils.textOrDefault(JiraUtils.getNode(fields, "assignee", "displayName"), "Unassigned");
            JsonNode descriptionNode = JiraUtils.getNode(fields, "description");
            String descriptionText = JiraUtils.extractDescription(descriptionNode);
            String descriptionLine = JiraUtils.buildDescriptionLine(descriptionNode, descriptionText);

            return String.format(
                    "[%s] %s (%s) | Assignee: %s | Priority: %s%n%s",
                    issueKey, summary, status, assignee, priority, descriptionLine);
        } catch (JiraClientException e) {
            return JiraUtils.formatError(e);
        } catch (RuntimeException e) {
            return JiraUtils.formatError(e);
        }
    }

    @Tool("Search Jira issues using JQL. Returns up to 5 results.")
    public String searchIssues(@P("JQL query string") String jql) {
        try {
            JsonNode searchResponse = client.searchIssues(jql, DEFAULT_MAX_RESULTS);
            JsonNode issues = searchResponse.get("issues");
            if (issues == null || !issues.isArray() || issues.isEmpty()) {
                return "No issues found.";
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode issue : issues) {
                String issueKey = JiraUtils.textOrDefault(issue.get("key"), "(unknown key)");
                JsonNode fields = issue.get("fields");
                String summary = JiraUtils.textOrDefault(JiraUtils.getNode(fields, "summary"), "(no summary)");
                String status = JiraUtils.textOrDefault(JiraUtils.getNode(fields, "status", "name"), "Unknown");
                String priority = JiraUtils.textOrDefault(JiraUtils.getNode(fields, "priority", "name"), "Unspecified");
                String assignee =
                        JiraUtils.textOrDefault(JiraUtils.getNode(fields, "assignee", "displayName"), "Unassigned");
                if (sb.length() > 0) {
                    sb.append(System.lineSeparator());
                }
                sb.append(String.format(
                        "- [%s] %s (%s) | Assignee: %s | Priority: %s", issueKey, summary, status, assignee, priority));
            }
            return sb.toString();
        } catch (JiraClientException e) {
            return JiraUtils.formatError(e);
        } catch (RuntimeException e) {
            return JiraUtils.formatError(e);
        }
    }

    @Tool("Create a Jira issue with summary and description.")
    public String createIssue(
            @P("Project key, e.g. PROJ") String projectKey,
            @P("Issue summary") String summary,
            @P("Issue description text") String description,
            @P("Issue type name, default Task") String issueType,
            @P("Priority name, default Medium") String priority) {
        try {
            ObjectNode fields = OBJECT_MAPPER.createObjectNode();
            fields.putObject("project").put("key", projectKey);
            fields.put("summary", summary);
            fields.putObject("issuetype").put("name", JiraUtils.defaultIfBlank(issueType, "Task"));
            fields.putObject("priority").put("name", JiraUtils.defaultIfBlank(priority, "Medium"));
            fields.set("description", JiraUtils.toADF(description));

            ObjectNode payload = OBJECT_MAPPER.createObjectNode();
            payload.set("fields", fields);

            JsonNode response = client.createIssue(payload);
            String key = JiraUtils.textOrDefault(response.get("key"), "(unknown key)");
            return "Created issue [" + key + "]";
        } catch (JiraClientException e) {
            return JiraUtils.formatError(e);
        } catch (RuntimeException e) {
            return JiraUtils.formatError(e);
        }
    }

    @Tool("Add a comment to a Jira issue.")
    public String addComment(@P("Issue key, e.g. PROJ-123") String issueKey, @P("Comment text") String body) {
        try {
            ObjectNode payload = OBJECT_MAPPER.createObjectNode();
            payload.set("body", JiraUtils.toADF(body));
            client.addComment(issueKey, payload);
            return "Comment added";
        } catch (JiraClientException e) {
            return JiraUtils.formatError(e);
        } catch (RuntimeException e) {
            return JiraUtils.formatError(e);
        }
    }

    /**
     * Builder for {@link JiraTool}.
     */
    public static final class Builder {
        private String baseUrl;
        private JiraClient.Authentication authentication;
        private Duration timeout = Duration.ofSeconds(30);
        private HttpClient httpClient;

        private Builder() {}

        /**
         * Sets Jira base URL, e.g. {@code https://your-domain.atlassian.net}.
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /**
         * Sets Jira authentication strategy.
         */
        public Builder authentication(JiraClient.Authentication authentication) {
            this.authentication = authentication;
            return this;
        }

        /**
         * Sets request timeout.
         */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
            return this;
        }

        /**
         * Sets a custom {@link HttpClient} for the underlying {@link JiraClient}.
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
            return this;
        }

        /**
         * Builds a {@link JiraTool} with an underlying {@link JiraClient}.
         */
        public JiraTool build() {
            JiraClient.Builder clientBuilder = JiraClient.builder()
                    .baseUrl(baseUrl)
                    .authentication(authentication)
                    .timeout(timeout);
            if (httpClient != null) {
                clientBuilder.httpClient(httpClient);
            }
            JiraClient client = clientBuilder.build();
            return new JiraTool(client);
        }
    }
}
