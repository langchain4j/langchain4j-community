package dev.langchain4j.community.store.ememory.chat.redis;

public class RedisChatMemoryStoreException extends RuntimeException {

    public RedisChatMemoryStoreException(String message) {
        super(message);
    }
}
