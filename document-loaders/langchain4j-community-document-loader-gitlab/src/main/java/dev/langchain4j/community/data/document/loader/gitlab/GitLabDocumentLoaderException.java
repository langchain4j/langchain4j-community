package dev.langchain4j.community.data.document.loader.gitlab;

import dev.langchain4j.exception.LangChain4jException;

public class GitLabDocumentLoaderException extends LangChain4jException {

    public GitLabDocumentLoaderException(String message, Throwable cause) {
        super(message, cause);
    }
}
