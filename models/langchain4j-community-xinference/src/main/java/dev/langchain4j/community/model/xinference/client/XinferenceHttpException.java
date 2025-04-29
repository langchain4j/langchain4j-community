package dev.langchain4j.community.model.xinference.client;

import dev.langchain4j.exception.LangChain4jException;

public class XinferenceHttpException extends LangChain4jException {
    private final int code;

    public XinferenceHttpException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
