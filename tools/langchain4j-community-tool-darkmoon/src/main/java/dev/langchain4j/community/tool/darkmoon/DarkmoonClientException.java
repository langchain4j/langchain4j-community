package dev.langchain4j.community.tool.darkmoon;

/**
 * Thrown when a call to the Darkmoon Dashboard API fails.
 */
final class DarkmoonClientException extends RuntimeException {

    private final int statusCode;

    DarkmoonClientException(String message) {
        this(-1, message, null);
    }

    DarkmoonClientException(String message, Throwable cause) {
        this(-1, message, cause);
    }

    DarkmoonClientException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * The HTTP status code returned by the dashboard, or {@code -1} when the
     * failure happened before a response was received (e.g. malformed JSON).
     *
     * @return the HTTP status code, or {@code -1}
     */
    int statusCode() {
        return statusCode;
    }
}
