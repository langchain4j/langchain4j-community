package dev.langchain4j.community.store.embedding.duckdb;

import dev.langchain4j.exception.LangChain4jException;

public class DuckDBSQLException extends LangChain4jException {

    public DuckDBSQLException(String message, Throwable cause) {
        super(message, cause);
    }
}
