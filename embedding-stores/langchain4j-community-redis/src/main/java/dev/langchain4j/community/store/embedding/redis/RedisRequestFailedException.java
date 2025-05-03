package dev.langchain4j.community.store.embedding.redis;

import dev.langchain4j.exception.LangChain4jException;

/**
 * Exception thrown when a request to Redis fails.
 * This includes network issues, server errors, or invalid response formats.
 */
public class RedisRequestFailedException extends LangChain4jException {

    /**
     * Creates a new RedisRequestFailedException with the specified error message.
     *
     * @param message The error message
     */
    public RedisRequestFailedException(String message) {
        super(message);
    }

    /**
     * Creates a new RedisRequestFailedException with the specified error message and cause.
     *
     * @param message The error message
     * @param cause The cause of the exception
     */
    public RedisRequestFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
