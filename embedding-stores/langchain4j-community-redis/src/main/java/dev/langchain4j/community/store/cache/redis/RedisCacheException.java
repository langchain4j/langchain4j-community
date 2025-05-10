package dev.langchain4j.community.store.cache.redis;

/**
 * Exception thrown when operations on the Redis cache fail.
 */
public class RedisCacheException extends RuntimeException {

    /**
     * Creates a new RedisCacheException with the specified message.
     *
     * @param message The exception message
     */
    public RedisCacheException(String message) {
        super(message);
    }

    /**
     * Creates a new RedisCacheException with the specified message and cause.
     *
     * @param message The exception message
     * @param cause The underlying cause
     */
    public RedisCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
