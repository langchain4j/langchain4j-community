package dev.langchain4j.model.router;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * A {@link ChatModel} implementation that routes requests to other chat models
 * using a provided routing strategy.
 *
 * <p>Streaming models are routed by {@link StreamingModelRouter} instead: since
 * {@link ChatModel} and {@link dev.langchain4j.model.chat.StreamingChatModel} define conflicting
 * {@code chat(...)}/{@code doChat(...)} signatures, a single router cannot implement both.
 *
 * <p>Usage example:
 * <pre>{@code
 * ChatModel oneModel = ...;
 * ChatModel otherModel = ...;
 *
 * ModelRouter router = ModelRouter.builder()
 *         .addRoutes(oneModel, otherModel)
 *         .routingStrategy(new FailoverStrategy())
 *         .build();
 *
 * ChatResponse response = router.chat(ChatRequest.userMessage("Explain this complex topic"));
 * }
 * </pre>
 */
@Experimental
public class ModelRouter implements ChatModel {

    private final List<ChatModelWrapper> routes;
    private final ModelRoutingStrategy routingStrategy;
    private final ChatModelWrapper defaultTarget;

    private ModelRouter(Builder builder) {
        this.routes = Collections.unmodifiableList(ensureNotNull(builder.routes, "routes"));
        this.routingStrategy = ensureNotNull(builder.routingStrategy, "routingStrategy");
        this.defaultTarget = builder.defaultRoute;
    }

    public static Builder builder() {
        return new Builder();
    }

    protected ChatModelWrapper resolveDelegate(ChatRequest chatRequest) {
        // safe by the ModelRoutingStrategy contract: strategies must return one of the
        // given availableModels, which are all ChatModelWrappers for this router
        ChatModelWrapper target = (ChatModelWrapper) routingStrategy.route(routes, chatRequest);
        if (target == null) {
            target = defaultTarget;
        }
        if (target == null) {
            throw new IllegalStateException("No matching route for request found");
        }
        return target;
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        // Bound the failover retry to the number of configured routes: each route is
        // given a single attempt before the original failure propagates. Without this
        // bound, a persistently-failing delegate would cause unbounded recursion and a
        // StackOverflowError instead of surfacing the underlying failure.
        return doChatInternal(chatRequest, this.routes.size());
    }

    private ChatResponse doChatInternal(ChatRequest chatRequest, int attemptsLeft) {
        ChatModelWrapper delegate = resolveDelegate(chatRequest);
        try {
            return delegate.chat(chatRequest);
        } catch (NoMatchingModelFoundException e) {
            throw e;
        } catch (Exception e) {
            if (attemptsLeft <= 1) {
                throw e;
            }
            return doChatInternal(chatRequest, attemptsLeft - 1);
        }
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return Collections.emptySet();
    }

    @Override
    public ModelProvider provider() {
        return ModelProvider.OTHER;
    }

    @Override
    public List<ChatModelListener> listeners() {
        return List.of();
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return DefaultChatRequestParameters.EMPTY;
    }

    public static final class Builder {
        private final List<ChatModelWrapper> routes = new ArrayList<>();
        private ModelRoutingStrategy routingStrategy;
        private ChatModelWrapper defaultRoute;

        public Builder addRoutes(ChatModel... model) {
            for (ChatModel chatModel : model) {
                this.routes.add(new ChatModelWrapper(chatModel));
            }
            return this;
        }

        public Builder defaultRoute(ChatModel model) {
            this.defaultRoute = new ChatModelWrapper(model);
            return this;
        }

        public Builder routingStrategy(ModelRoutingStrategy routingStrategy) {
            this.routingStrategy = routingStrategy;
            return this;
        }

        public ModelRouter build() {
            return new ModelRouter(this);
        }
    }
}
