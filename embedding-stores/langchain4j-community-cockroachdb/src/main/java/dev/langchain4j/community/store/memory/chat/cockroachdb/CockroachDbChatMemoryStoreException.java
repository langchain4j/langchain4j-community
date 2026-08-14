package dev.langchain4j.community.store.memory.chat.cockroachdb;

public class CockroachDbChatMemoryStoreException extends RuntimeException {

    public CockroachDbChatMemoryStoreException(String message) {
        super(message);
    }

    public CockroachDbChatMemoryStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
