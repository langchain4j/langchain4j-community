package dev.langchain4j.community.store.memory.chat.redis;

import dev.langchain4j.exception.LangChain4jException;

public class RedisChatMemoryStoreException extends LangChain4jException {

    public RedisChatMemoryStoreException(String message) {
        super(message);
    }
}
