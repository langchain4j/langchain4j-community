package dev.langchain4j.community.model.zhipu;

import dev.langchain4j.exception.LangChain4jException;

public class ZhipuAiException extends LangChain4jException {

    /**
     * error code see <a href="https://open.bigmodel.cn/dev/api#error-code-v3">error codes document</a>
     */
    private String code;

    public ZhipuAiException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ZhipuAiException(String message) {
        super(message);
    }

    public String getCode() {
        return code;
    }
}
