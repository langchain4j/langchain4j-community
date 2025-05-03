package dev.langchain4j.community.store.memory.chat.redis;

import dev.langchain4j.exception.LangChain4jException;

/**
 * Exception thrown when an error occurs during Redis chat memory store operations.
 * This includes connection issues, data serialization/deserialization problems,
 * or any other failures that occur when interacting with Redis for chat memory persistence.
 */
public class RedisChatMemoryStoreException extends LangChain4jException {

    /**
     * Creates a new RedisChatMemoryStoreException with the specified error message.
     *
     * @param message The error message
     */
    public RedisChatMemoryStoreException(String message) {
        super(message);
    }
}
