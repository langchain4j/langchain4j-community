package dev.langchain4j.model.router;

import java.util.List;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequest;

/**
 * Encapsulates the logic for choosing which model to route a chat request to.
 */
@Experimental
@FunctionalInterface
public interface ModelRoutingStrategy {
    
    /**
     * Determines the route key to use for the given chat messages.
     *
     * @param availableModels
     *            all configured models, including any routing metadata
     * @param chatRequest
     *            the incoming chat request
     * @return the key of the route to use
     */
	ChatModelWrapper route(List<ChatModelWrapper> availableModels, ChatRequest chatRequest);
}
