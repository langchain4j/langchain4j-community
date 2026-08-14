package dev.langchain4j.community.store.embedding.valkey;

import dev.langchain4j.exception.LangChain4jException;

/**
 * Exception thrown when a Valkey operation fails.
 */
public class ValkeyRequestFailedException extends LangChain4jException {

    public ValkeyRequestFailedException(String message) {
        super(message);
    }

    public ValkeyRequestFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
