package dev.langchain4j.community.model.minimax.client;

import dev.langchain4j.exception.LangChain4jException;

public class MiniMaxHttpException extends LangChain4jException {
    private final int code;

    public MiniMaxHttpException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
