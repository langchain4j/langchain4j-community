package dev.langchain4j.community.rag.content.retriever.neo4j;

import dev.langchain4j.exception.LangChain4jException;

public class Neo4jException extends LangChain4jException {

    public Neo4jException(String message, Throwable cause) {
        super(message, cause);
    }
}
