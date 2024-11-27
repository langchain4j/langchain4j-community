package dev.langchain4j.community.model.xinference.client;

public class XinferenceHttpException extends RuntimeException {
    private final int code;

    public XinferenceHttpException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int code() {
        return code;
    }
}
