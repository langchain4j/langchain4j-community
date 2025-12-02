package dev.langchain4j.community.store.memory.chat.duckdb;

import dev.langchain4j.exception.LangChain4jException;

public class DuckDBChatMemoryException extends LangChain4jException {

    public DuckDBChatMemoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
