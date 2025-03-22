package dev.langchain4j.rag.content.retriever.neo4j;

public class Neo4jException extends RuntimeException {

    public Neo4jException(String message, Throwable cause) {

        super(message, cause);
    }
}
