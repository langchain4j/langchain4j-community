package dev.langchain4j.community.tool.datacircle;

/**
 * Raised when a Datacircle API request fails.
 */
final class DatacircleClientException extends RuntimeException {

    /** HTTP status, or -1 when the request received no HTTP response. */
    private final int statusCode;

    DatacircleClientException(String message) {
        this(-1, message, null);
    }

    DatacircleClientException(String message, Throwable cause) {
        this(-1, message, cause);
    }

    DatacircleClientException(int statusCode, String message, Throwable cause) {
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
