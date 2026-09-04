package dev.langchain4j.model.router;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;

/**
 * A {@link StreamingChatModel} implementation that routes requests to other streaming chat models
 * using a provided routing strategy.
 *
 * <p>Synchronous models are routed by {@link ModelRouter} instead: since
 * {@link dev.langchain4j.model.chat.ChatModel} and {@link StreamingChatModel} define conflicting
 * {@code chat(...)}/{@code doChat(...)} signatures, a single router cannot implement both.
 *
 * <p>Usage example:
 * <pre>{@code
 * StreamingChatModel oneModel = ...;
 * StreamingChatModel otherModel = ...;
 *
 * StreamingModelRouter router = StreamingModelRouter.builder()
 *         .addRoutes(oneModel, otherModel)
 *         .routingStrategy(new FailoverStrategy())
 *         .build();
 *
 * router.chat(ChatRequest.userMessage("Explain this complex topic"), handler);
 * }
 * </pre>
 */
@Experimental
public class StreamingModelRouter implements StreamingChatModel {

    private final List<StreamingChatModelWrapper> routes;
    private final ModelRoutingStrategy routingStrategy;
    private final StreamingChatModelWrapper defaultTarget;

    private StreamingModelRouter(Builder builder) {
        this.routes = Collections.unmodifiableList(ensureNotNull(builder.routes, "routes"));
        this.routingStrategy = ensureNotNull(builder.routingStrategy, "routingStrategy");
        this.defaultTarget = builder.defaultRoute;
    }

    public static Builder builder() {
        return new Builder();
    }

    protected StreamingChatModelWrapper resolveDelegate(ChatRequest chatRequest) {
        // safe by the ModelRoutingStrategy contract: strategies must return one of the
        // given availableModels, which are all StreamingChatModelWrappers for this router
        StreamingChatModelWrapper target = (StreamingChatModelWrapper) routingStrategy.route(routes, chatRequest);
        if (target == null) {
            target = defaultTarget;
        }
        if (target == null) {
            throw new IllegalStateException("No matching route for request found");
        }
        return target;
    }

    /**
     * Handler-based entry point. Failover applies to exceptions thrown synchronously by the
     * delegate invocation only; asynchronous errors are reported to the handler unchanged, as
     * they were before the reactive API existed.
     */
    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        // Bound the failover retry to the number of configured routes: each route is
        // given a single attempt before the original failure propagates. Without this
        // bound, a persistently-failing delegate would cause unbounded recursion and a
        // StackOverflowError instead of surfacing the underlying failure.
        doChatInternal(chatRequest, handler, this.routes.size());
    }

    private void doChatInternal(ChatRequest chatRequest, StreamingChatResponseHandler handler, int attemptsLeft) {
        StreamingChatModelWrapper delegate = resolveDelegate(chatRequest);
        try {
            delegate.chat(chatRequest, handler);
        } catch (NoMatchingModelFoundException e) {
            throw e;
        } catch (Exception e) {
            if (attemptsLeft <= 1) {
                throw e;
            }
            doChatInternal(chatRequest, handler, attemptsLeft - 1);
        }
    }

    /**
     * Reactive entry point. The returned {@code Publisher} subscribes to the route selected by the
     * routing strategy. If that route fails before emitting any event, the subscription is retried
     * on the next route selected by the strategy, up to the number of configured routes (mirroring
     * the bounded failover of the handler-based and synchronous paths). Once an event has been
     * emitted, a failure is propagated to the subscriber instead of being retried.
     */
    @Override
    public Publisher<ChatModelStreamingEvent> doChat(ChatRequest chatRequest) {
        return downstream -> new FailoverSubscription(chatRequest, downstream, routes.size()).start();
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
        private final List<StreamingChatModelWrapper> routes = new ArrayList<>();
        private ModelRoutingStrategy routingStrategy;
        private StreamingChatModelWrapper defaultRoute;

        public Builder addRoutes(StreamingChatModel... model) {
            for (StreamingChatModel streamingChatModel : model) {
                this.routes.add(new StreamingChatModelWrapper(streamingChatModel));
            }
            return this;
        }

        public Builder defaultRoute(StreamingChatModel model) {
            this.defaultRoute = new StreamingChatModelWrapper(model);
            return this;
        }

        public Builder routingStrategy(ModelRoutingStrategy routingStrategy) {
            this.routingStrategy = routingStrategy;
            return this;
        }

        public StreamingModelRouter build() {
            return new StreamingModelRouter(this);
        }
    }

    /**
     * The {@link Subscription} exposed to the subscriber of {@link #doChat(ChatRequest)}. It
     * subscribes to one route at a time and fails over to the next selected route when the current
     * one fails before emitting any event.
     */
    private final class FailoverSubscription implements Subscription {

        private final ChatRequest chatRequest;
        private final Subscriber<? super ChatModelStreamingEvent> downstream;
        private volatile int attemptsLeft;
        private volatile boolean emitted;
        private volatile boolean cancelled;
        private Subscription current; // guarded by this
        private long totalRequested; // guarded by this
        private long forwarded; // guarded by this

        FailoverSubscription(
                ChatRequest chatRequest, Subscriber<? super ChatModelStreamingEvent> downstream, int attempts) {
            this.chatRequest = chatRequest;
            this.downstream = downstream;
            this.attemptsLeft = attempts;
        }

        void start() {
            downstream.onSubscribe(this);
            subscribeNextRoute();
        }

        private void subscribeNextRoute() {
            if (cancelled) {
                return;
            }
            StreamingChatModelWrapper delegate;
            try {
                delegate = resolveDelegate(chatRequest);
            } catch (Throwable error) {
                downstream.onError(error);
                return;
            }
            Publisher<ChatModelStreamingEvent> publisher;
            try {
                publisher = delegate.chat(chatRequest);
            } catch (Throwable error) {
                failoverOrPropagate(error);
                return;
            }
            try {
                publisher.subscribe(new FailoverSubscriber());
            } catch (Throwable error) {
                failoverOrPropagate(error);
            }
        }

        private void failoverOrPropagate(Throwable error) {
            attemptsLeft--;
            if (!emitted && !cancelled && attemptsLeft > 0) {
                synchronized (this) {
                    current = null;
                    // no event was delivered to the downstream subscriber, so the full
                    // outstanding demand must be granted to the next route
                    forwarded = 0;
                }
                subscribeNextRoute();
            } else {
                downstream.onError(error);
            }
        }

        private void register(Subscription subscription) {
            long toForward;
            synchronized (this) {
                if (cancelled) {
                    subscription.cancel();
                    return;
                }
                current = subscription;
                toForward = totalRequested - forwarded;
                forwarded = totalRequested;
            }
            if (toForward > 0) {
                subscription.request(toForward);
            }
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                throw new IllegalArgumentException("non-positive request: " + n);
            }
            Subscription subscription;
            long toForward;
            synchronized (this) {
                totalRequested = saturatingAdd(totalRequested, n);
                subscription = current;
                if (subscription != null) {
                    toForward = totalRequested - forwarded;
                    forwarded = totalRequested;
                } else {
                    toForward = 0;
                }
            }
            if (toForward > 0) {
                subscription.request(toForward);
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
            Subscription subscription;
            synchronized (this) {
                subscription = current;
            }
            if (subscription != null) {
                subscription.cancel();
            }
        }

        private final class FailoverSubscriber implements Subscriber<ChatModelStreamingEvent> {

            @Override
            public void onSubscribe(Subscription subscription) {
                register(subscription);
            }

            @Override
            public void onNext(ChatModelStreamingEvent event) {
                emitted = true;
                downstream.onNext(event);
            }

            @Override
            public void onError(Throwable error) {
                failoverOrPropagate(error);
            }

            @Override
            public void onComplete() {
                downstream.onComplete();
            }
        }
    }

    private static long saturatingAdd(long a, long b) {
        long sum = a + b;
        return sum < 0 ? Long.MAX_VALUE : sum;
    }
}
