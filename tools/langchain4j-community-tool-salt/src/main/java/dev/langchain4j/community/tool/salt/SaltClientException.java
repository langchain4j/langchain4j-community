package dev.langchain4j.community.tool.salt;

/**
 * Thrown when a call to the Salt API fails.
 */
final class SaltClientException extends RuntimeException {

    private final int statusCode;

    SaltClientException(String message) {
        this(-1, message, null);
    }

    SaltClientException(String message, Throwable cause) {
        this(-1, message, cause);
    }

    SaltClientException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * The HTTP status code returned by Salt, or {@code -1} when the failure
     * happened before a response was received (e.g. malformed JSON).
     *
     * @return the HTTP status code, or {@code -1}
     */
    int statusCode() {
        return statusCode;
    }
}
