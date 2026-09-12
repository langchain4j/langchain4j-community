package dev.langchain4j.community.tool.livetennis;

/**
 * Raised when a Live Tennis API request fails.
 */
final class LiveTennisClientException extends RuntimeException {

    /** HTTP status, or -1 when the request received no HTTP response. */
    private final int statusCode;

    LiveTennisClientException(String message) {
        this(-1, message, null);
    }

    LiveTennisClientException(String message, Throwable cause) {
        this(-1, message, cause);
    }

    LiveTennisClientException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * Returns the HTTP status, or {@code -1} when no response was received.
     *
     * @return HTTP status or -1
     */
    int statusCode() {
        return statusCode;
    }
}
