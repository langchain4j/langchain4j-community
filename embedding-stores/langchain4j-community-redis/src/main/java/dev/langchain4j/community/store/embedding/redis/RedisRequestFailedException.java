package dev.langchain4j.community.store.embedding.redis;

import dev.langchain4j.exception.LangChain4jException;

public class RedisRequestFailedException extends LangChain4jException {

    public RedisRequestFailedException(String message) {
        super(message);
    }

    public RedisRequestFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
