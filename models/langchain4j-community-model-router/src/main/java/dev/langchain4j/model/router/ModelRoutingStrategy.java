package dev.langchain4j.model.router;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;

/**
 * Encapsulates the logic for choosing which model to route a chat request to.
 */
@Experimental
@FunctionalInterface
public interface ModelRoutingStrategy {

    /**
     * Determines the route to use for the given chat request.
     *
     * <p>The same strategy can be used with both {@link ModelRouter} (whose routes are
     * {@link ChatModelWrapper}s) and {@link StreamingModelRouter} (whose routes are
     * {@link StreamingChatModelWrapper}s). Implementations must return one of the given
     * {@code availableModels} (or {@code null}).
     *
     * @param availableModels
     *            all configured models, including any routing metadata
     * @param chatRequest
     *            the incoming chat request
     * @return the route to use, or {@code null} if no route matches
     */
    ModelWrapper route(List<? extends ModelWrapper> availableModels, ChatRequest chatRequest);
}
