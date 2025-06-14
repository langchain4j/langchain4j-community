package dev.langchain4j.community.store.embedding.typesense.exception;

import dev.langchain4j.exception.LangChain4jException;

public class TypesenseException extends LangChain4jException {

    public TypesenseException(String message) {
        super(message);
    }

    public TypesenseException(String message, Throwable cause) {
        super(message, cause);
    }
}
