package dev.langchain4j.community.tool.jira;

/**
 * Exception thrown for non-2xx Jira responses or client errors.
 */
public final class JiraClientException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public JiraClientException(int statusCode, String responseBody) {
        super("Jira API request failed with status " + statusCode);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public JiraClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.responseBody = null;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
