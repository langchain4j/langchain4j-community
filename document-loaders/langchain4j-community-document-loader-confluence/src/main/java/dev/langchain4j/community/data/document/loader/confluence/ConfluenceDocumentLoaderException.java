package dev.langchain4j.community.data.document.loader.confluence;

import dev.langchain4j.exception.LangChain4jException;

public class ConfluenceDocumentLoaderException extends LangChain4jException {

    public ConfluenceDocumentLoaderException(String message, Throwable cause) {
        super(message, cause);
    }
}
