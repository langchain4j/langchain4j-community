package dev.langchain4j.model.router;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A {@link ChatModel} implementation that routes requests to other chat models
 * using a provided routing strategy.
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
public class ModelRouter implements ChatModel, StreamingChatModel {

    private final List<ChatModelWrapper> routes;
    private final ModelRoutingStrategy routingStrategy;
    private final ChatModelWrapper defaultTarget;

    private ModelRouter(Builder builder) {
        this.routes = Collections.unmodifiableList(Objects.requireNonNull(builder.routes, "routes"));
        this.routingStrategy = Objects.requireNonNull(builder.routingStrategy, "routingStrategy");
        this.defaultTarget = builder.defaultRoute;
    }

    public static Builder builder() {
        return new Builder();
    }

    protected ChatModelWrapper resolveDelegate(ChatRequest chatRequest) {
        ChatModelWrapper target = routingStrategy.route(routes, chatRequest);
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
        ChatModelWrapper delegate = resolveDelegate(chatRequest);
        try {
            ChatResponse response = delegate.chat(chatRequest);
            return response;
        } catch (NoMatchingModelFoundException e) {
            throw e;
        } catch (Exception e) {
            return doChat(chatRequest);
        }
    }

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        ChatModelWrapper delegate = resolveDelegate(chatRequest);
        try {
            delegate.chat(chatRequest, handler);
        } catch (NoMatchingModelFoundException e) {
            throw e;
        } catch (Exception e) {
            doChat(chatRequest, handler);
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

        public Builder addRoutes(StreamingChatModel... model) {
            for (StreamingChatModel chatModel : model) {
                this.routes.add(new ChatModelWrapper(chatModel));
            }
            return this;
        }

        public Builder defaultRoute(ChatModel model) {
            this.defaultRoute = new ChatModelWrapper(model);
            return this;
        }

        public Builder defaultRoute(StreamingChatModel model) {
            this.defaultRoute = new ChatModelWrapper(model);
            return this;
        }

        public Builder routingStrategy(ModelRoutingStrategy routingStrategy) {
            this.routingStrategy = routingStrategy;
            return this;
        }

        public ModelRouter build() {
            Boolean streaming = null;
            for (ChatModelWrapper chatModelWrapper : routes) {
                if (chatModelWrapper.isStreaming()) {
                    if (streaming == null) {
                        streaming = true;
                    } else {
                        throw new RuntimeException("you cannot mix streaming and non streaming models");
                    }
                }
            }
            if (streaming != null) {
                if (defaultRoute.isStreaming() && !streaming) {
                    throw new RuntimeException("you cannot mix streaming and non streaming models");
                }
            }
            return new ModelRouter(this);
        }
    }
}
