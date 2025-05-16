package dev.langchain4j.community.store.session.redis;

/**
 * Exception thrown by Redis session manager implementations.
 */
public class RedisSessionException extends RuntimeException {

    /**
     * Creates a new exception with the specified message.
     *
     * @param message The error message
     */
    public RedisSessionException(String message) {
        super(message);
    }

    /**
     * Creates a new exception with the specified message and cause.
     *
     * @param message The error message
     * @param cause The cause of the exception
     */
    public RedisSessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
