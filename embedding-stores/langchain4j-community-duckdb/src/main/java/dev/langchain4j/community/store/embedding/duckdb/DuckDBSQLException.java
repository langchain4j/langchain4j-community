package dev.langchain4j.community.store.embedding.duckdb;

public class DuckDBSQLException extends RuntimeException {

    public DuckDBSQLException(String message, Throwable cause) {
        super(message, cause);
    }
}
