package dev.langchain4j.model.router;

import dev.langchain4j.Experimental;
import dev.langchain4j.exception.LangChain4jException;

@Experimental
public class NoMatchingModelFoundException extends LangChain4jException {

	private static final long serialVersionUID = 1L;
	
	public NoMatchingModelFoundException(String message) {
		super(message);
	}
}
