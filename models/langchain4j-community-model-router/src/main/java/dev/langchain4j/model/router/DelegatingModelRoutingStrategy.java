package dev.langchain4j.model.router;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.chat.request.ChatRequest;
import java.util.List;

/**
 * Base class for strategies that can optionally delegate parts of the routing decision to
 * another strategy.
 */
@Experimental
public abstract class DelegatingModelRoutingStrategy implements ModelRoutingStrategy {

    protected final ModelRoutingStrategy delegate;

    protected DelegatingModelRoutingStrategy() {
        this(null);
    }

    protected DelegatingModelRoutingStrategy(ModelRoutingStrategy delegate) {
        this.delegate = delegate;
    }

    protected ChatModelWrapper delegateRoute(List<ChatModelWrapper> availableModels, ChatRequest chatRequest) {
        if (delegate == null) {
            return null;
        }
        return delegate.route(availableModels, chatRequest);
    }
}
