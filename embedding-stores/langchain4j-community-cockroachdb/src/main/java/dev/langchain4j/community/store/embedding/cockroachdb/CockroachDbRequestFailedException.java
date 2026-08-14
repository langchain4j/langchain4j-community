package dev.langchain4j.community.store.embedding.cockroachdb;

public class CockroachDbRequestFailedException extends RuntimeException {

    public CockroachDbRequestFailedException(String message) {
        super(message);
    }

    public CockroachDbRequestFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
