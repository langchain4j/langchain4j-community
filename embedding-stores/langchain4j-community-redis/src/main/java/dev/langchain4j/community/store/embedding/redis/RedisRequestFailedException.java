package dev.langchain4j.community.store.embedding.redis;

public class RedisRequestFailedException extends RuntimeException {

    public RedisRequestFailedException() {
        super();
    }

    public RedisRequestFailedException(String message) {
        super(message);
    }

    public RedisRequestFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
