package dev.langchain4j.community.store.embedding.yugabytedb;

import dev.langchain4j.exception.LangChain4jException;

/**
 * Exception thrown when YugabyteDB operations fail
 *
 * This exception is used to wrap and indicate failures in YugabyteDB operations
 * such as connection errors, SQL execution failures, or data serialization issues.
 */
public class YugabyteDBRequestFailedException extends LangChain4jException {

    /**
     * Constructs a new YugabyteDB request failed exception with the specified detail message
     *
     * @param message the detail message
     */
    public YugabyteDBRequestFailedException(String message) {
        super(message);
    }

    /**
     * Constructs a new YugabyteDB request failed exception with the specified detail message and cause
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public YugabyteDBRequestFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
