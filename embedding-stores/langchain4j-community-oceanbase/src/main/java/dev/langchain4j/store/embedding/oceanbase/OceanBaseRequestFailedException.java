package dev.langchain4j.store.embedding.oceanbase;

import dev.langchain4j.exception.LangChain4jException;

class OceanBaseRequestFailedException extends LangChain4jException {

    public OceanBaseRequestFailedException(String message) {
        super(message);
    }

    public OceanBaseRequestFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
