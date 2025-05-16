package dev.langchain4j.community.store.cache.embedding.redis;

/**
 * Exception thrown when operations on a Redis embedding cache fail.
 */
public class RedisEmbeddingCacheException extends RuntimeException {

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message The detail message
     */
    public RedisEmbeddingCacheException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     *
     * @param message The detail message
     * @param cause The cause of the exception
     */
    public RedisEmbeddingCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
