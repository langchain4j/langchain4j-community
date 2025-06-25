package dev.langchain4j.community.store.embedding.oceanbase;

import dev.langchain4j.exception.LangChain4jException;

/**
 * Exception for OceanBase SQL related errors
 */
public class UncheckSQLException extends LangChain4jException {

    public UncheckSQLException(String message, Throwable cause) {
        super(message, cause);
    }

    public UncheckSQLException(String message) {
        super(message);
    }

    public UncheckSQLException(Throwable cause) {
        super("SQL error: " + cause.getMessage(), cause);
    }
}
