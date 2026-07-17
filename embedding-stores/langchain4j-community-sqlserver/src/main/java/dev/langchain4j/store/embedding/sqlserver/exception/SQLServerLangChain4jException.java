package dev.langchain4j.store.embedding.sqlserver.exception;

import dev.langchain4j.exception.LangChain4jException;
import java.io.Serial;

/**
 * Exception specific to SQL Server operations within LangChain4j.
 */
public class SQLServerLangChain4jException extends LangChain4jException {

    @Serial
    private static final long serialVersionUID = -8750324500800275924L;

    /**
     * Constructs a new instance of SQLServerLangChain4jException with the specified cause.
     *
     * @param cause The underlying cause of the exception. Can be null.
     */
    public SQLServerLangChain4jException(final Throwable cause) {
        super(cause);
    }

    /**
     * Creates a new instance of SQLServerLangChain4jException with the specified message and cause.
     * @param message Error message.
     * @param cause The underlying cause of the exception. Can be null.
     */
    public SQLServerLangChain4jException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
