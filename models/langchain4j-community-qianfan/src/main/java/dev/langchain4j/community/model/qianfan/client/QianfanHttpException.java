package dev.langchain4j.community.model.qianfan.client;

import dev.langchain4j.exception.LangChain4jException;

public class QianfanHttpException extends LangChain4jException {

    private final int code;

    public QianfanHttpException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return this.code;
    }
}
