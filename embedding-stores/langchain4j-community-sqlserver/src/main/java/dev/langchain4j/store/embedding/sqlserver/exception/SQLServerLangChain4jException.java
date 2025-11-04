package dev.langchain4j.store.embedding.sqlserver.exception;

import dev.langchain4j.exception.LangChain4jException;
import java.io.Serial;

public class SQLServerLangChain4jException extends LangChain4jException {

    @Serial
    private static final long serialVersionUID = -8750324500800275924L;

    public SQLServerLangChain4jException(final Throwable cause) {
        super(cause);
    }

    public SQLServerLangChain4jException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
