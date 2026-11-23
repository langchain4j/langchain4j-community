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
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.PartialResponseContext;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialThinkingContext;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.PartialToolCallContext;
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
     * Handler-based entry point. Failover applies to a failure a delegate reports before the delegate
     * invocation returns and before any event reaches the handler; a failure reported later, on another
     * thread, or after an event was delivered is passed to the handler unchanged.
     * <p>
     * {@link StreamingChatModel#chat(ChatRequest, StreamingChatResponseHandler)} does not throw: a model
     * that fails synchronously reports the failure to {@link StreamingChatResponseHandler#onError(Throwable)}
     * on the calling thread before returning. The failure is therefore observed as an error rather than
     * caught as an exception.
     */
    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        // Bound the failover retry to the number of configured routes: each route is
        // given a single attempt before the original failure propagates. Without this
        // bound, a persistently-failing delegate would retry without end instead of
        // surfacing the underlying failure.
        new HandlerFailover(chatRequest, handler, Math.max(0, this.routes.size() - 1)).start();
    }

    /**
     * State for one handler-based call, deciding whether a reported failure is retried on the next
     * route or delivered to the handler.
     */
    private final class HandlerFailover implements StreamingChatResponseHandler {

        private final ChatRequest chatRequest;
        private final StreamingChatResponseHandler downstream;
        private final Thread callingThread = Thread.currentThread();

        private int retriesLeft; // guarded by this
        private boolean emitted; // guarded by this: an event reached the handler
        private boolean invoking; // guarded by this: a delegate invocation is in progress
        private boolean terminated; // guarded by this: the handler received its terminal signal
        private Throwable retryFailure; // guarded by this

        private HandlerFailover(ChatRequest chatRequest, StreamingChatResponseHandler downstream, int retriesLeft) {
            this.chatRequest = chatRequest;
            this.downstream = downstream;
            this.retriesLeft = retriesLeft;
        }

        private void start() {
            while (true) {
                StreamingChatModelWrapper delegate;
                try {
                    delegate = resolveDelegate(chatRequest);
                } catch (Throwable error) {
                    // A routing failure is not a delegate failure: retrying it would select the same route.
                    terminate(error);
                    return;
                }

                Throwable failure;
                synchronized (this) {
                    if (terminated) {
                        return;
                    }
                    invoking = true;
                    retryFailure = null;
                }
                try {
                    delegate.chat(chatRequest, this);
                } catch (Throwable error) {
                    // StreamingChatModel.chat(...) reports a model failure to onError, so this only covers
                    // a delegate that overrides chat(...) and still throws.
                    failed(error);
                } finally {
                    synchronized (this) {
                        invoking = false;
                        failure = retryFailure;
                        retryFailure = null;
                    }
                }

                if (failure == null) {
                    // The call either signalled terminally or is still running asynchronously.
                    return;
                }

                synchronized (this) {
                    retriesLeft--;
                }
            }
        }

        private void failed(Throwable error) {
            synchronized (this) {
                if (terminated) {
                    return;
                }
                // A synchronous failure is one reported on the calling thread while the delegate invocation
                // is still running. Retrying after an event was delivered would repeat output the handler
                // already received. A NoMatchingModelFoundException means the selection itself is wrong, so
                // retrying it would ask the same strategy again.
                if (invoking
                        && !emitted
                        && Thread.currentThread() == callingThread
                        && retriesLeft > 0
                        && !(error instanceof NoMatchingModelFoundException)) {
                    if (retryFailure == null) {
                        retryFailure = error;
                    }
                    return;
                }
            }
            terminate(error);
        }

        private void terminate(Throwable error) {
            synchronized (this) {
                if (terminated) {
                    return;
                }
                terminated = true;
            }
            downstream.onError(error);
        }

        private boolean beginEvent() {
            synchronized (this) {
                if (terminated) {
                    return false;
                }
                emitted = true;
                return true;
            }
        }

        @Override
        public void onPartialResponse(String partialResponse) {
            if (beginEvent()) {
                downstream.onPartialResponse(partialResponse);
            }
        }

        @Override
        public void onPartialResponse(PartialResponse partialResponse, PartialResponseContext context) {
            if (beginEvent()) {
                downstream.onPartialResponse(partialResponse, context);
            }
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking) {
            if (beginEvent()) {
                downstream.onPartialThinking(partialThinking);
            }
        }

        @Override
        public void onPartialThinking(PartialThinking partialThinking, PartialThinkingContext context) {
            if (beginEvent()) {
                downstream.onPartialThinking(partialThinking, context);
            }
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall) {
            if (beginEvent()) {
                downstream.onPartialToolCall(partialToolCall);
            }
        }

        @Override
        public void onPartialToolCall(PartialToolCall partialToolCall, PartialToolCallContext context) {
            if (beginEvent()) {
                downstream.onPartialToolCall(partialToolCall, context);
            }
        }

        @Override
        public void onCompleteToolCall(CompleteToolCall completeToolCall) {
            if (beginEvent()) {
                downstream.onCompleteToolCall(completeToolCall);
            }
        }

        @Override
        public void onUnmappedRawEvent(Object rawEvent) {
            if (beginEvent()) {
                downstream.onUnmappedRawEvent(rawEvent);
            }
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            synchronized (this) {
                if (terminated) {
                    return;
                }
                terminated = true;
            }
            downstream.onCompleteResponse(completeResponse);
        }

        @Override
        public void onError(Throwable error) {
            failed(error);
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
        private int attemptsLeft; // guarded by this
        private boolean emitted; // guarded by this
        private volatile boolean cancelled; // also true after a terminal signal is selected
        private int activeOnNext; // guarded by this
        private boolean terminalPending; // guarded by this
        private Throwable terminalError; // guarded by this
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
                terminate(error, false);
                return;
            }
            if (cancelled) {
                return;
            }
            Publisher<ChatModelStreamingEvent> publisher;
            try {
                publisher = delegate.chat(chatRequest);
            } catch (Throwable error) {
                failoverOrPropagate(error);
                return;
            }
            if (cancelled) {
                return;
            }
            try {
                publisher.subscribe(new FailoverSubscriber());
            } catch (Throwable error) {
                failoverOrPropagate(error);
            }
        }

        private void failoverOrPropagate(Throwable error) {
            boolean retry;
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                attemptsLeft--;
                retry = !emitted && attemptsLeft > 0;
                if (retry) {
                    current = null;
                    // no event was delivered to the downstream subscriber, so the full
                    // outstanding demand must be granted to the next route
                    forwarded = 0;
                }
            }
            if (retry) {
                subscribeNextRoute();
            } else {
                terminate(error, false);
            }
        }

        private void register(Subscription subscription) {
            long toForward = 0;
            boolean cancel;
            synchronized (this) {
                cancel = cancelled;
                if (!cancel) {
                    current = subscription;
                    toForward = totalRequested - forwarded;
                    forwarded = totalRequested;
                }
            }
            if (cancel) {
                subscription.cancel();
            } else if (toForward > 0) {
                subscription.request(toForward);
            }
        }

        private void terminate(Throwable error, boolean cancelUpstream) {
            Subscription subscription;
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                cancelled = true;
                subscription = current;
                current = null;
                terminalError = error;
                terminalPending = true;
            }
            // Cancel outside the monitor: an upstream may synchronously call us back.
            if (cancelUpstream && subscription != null) {
                subscription.cancel();
            }
            signalTerminal();
        }

        private void signalTerminal() {
            Throwable error;
            synchronized (this) {
                if (!terminalPending || activeOnNext != 0) {
                    return;
                }
                terminalPending = false;
                error = terminalError;
                terminalError = null;
            }
            if (error != null) {
                downstream.onError(error);
            } else {
                downstream.onComplete();
            }
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                terminate(new IllegalArgumentException("non-positive request: " + n), true);
                return;
            }
            Subscription subscription;
            long toForward;
            synchronized (this) {
                if (cancelled) {
                    return;
                }
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
            Subscription subscription;
            synchronized (this) {
                if (cancelled) {
                    return;
                }
                cancelled = true;
                subscription = current;
                current = null;
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
                synchronized (FailoverSubscription.this) {
                    if (cancelled) {
                        return;
                    }
                    emitted = true;
                    activeOnNext++;
                }
                try {
                    downstream.onNext(event);
                } finally {
                    synchronized (FailoverSubscription.this) {
                        activeOnNext--;
                    }
                    // An invalid request can arrive from within onNext or another thread.
                    // Deliver its terminal signal only after the in-flight callback returns.
                    signalTerminal();
                }
            }

            @Override
            public void onError(Throwable error) {
                failoverOrPropagate(error);
            }

            @Override
            public void onComplete() {
                terminate(null, false);
            }
        }
    }

    private static long saturatingAdd(long a, long b) {
        long sum = a + b;
        return sum < 0 ? Long.MAX_VALUE : sum;
    }
}
