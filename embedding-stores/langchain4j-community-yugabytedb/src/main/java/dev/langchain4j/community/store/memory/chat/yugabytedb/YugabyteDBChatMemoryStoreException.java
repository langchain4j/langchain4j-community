package dev.langchain4j.community.store.memory.chat.yugabytedb;

import dev.langchain4j.exception.LangChain4jException;

/**
 * Exception thrown when YugabyteDB chat memory operations fail
 *
 * This exception is used to wrap and indicate failures in YugabyteDB chat memory operations
 * such as connection errors, SQL execution failures, or data serialization issues.
 */
public class YugabyteDBChatMemoryStoreException extends LangChain4jException {

    /**
     * Constructs a new YugabyteDB chat memory store exception with the specified detail message
     *
     * @param message the detail message
     */
    public YugabyteDBChatMemoryStoreException(String message) {
        super(message);
    }

    /**
     * Constructs a new YugabyteDB chat memory store exception with the specified detail message and cause
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public YugabyteDBChatMemoryStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
