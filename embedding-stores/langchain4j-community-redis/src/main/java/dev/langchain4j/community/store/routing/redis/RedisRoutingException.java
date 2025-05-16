package dev.langchain4j.community.store.routing.redis;

/**
 * Exception class for Redis semantic routing errors.
 *
 * <p>This exception is thrown when there are errors in Redis operations
 * related to semantic routing, such as creating indexes, storing routes,
 * or executing semantic routing queries.</p>
 */
public class RedisRoutingException extends RuntimeException {

    /**
     * Creates a new RedisRoutingException with the specified message.
     *
     * @param message The exception message
     */
    public RedisRoutingException(String message) {
        super(message);
    }

    /**
     * Creates a new RedisRoutingException with the specified message and cause.
     *
     * @param message The exception message
     * @param cause The cause of the exception
     */
    public RedisRoutingException(String message, Throwable cause) {
        super(message, cause);
    }
}
