package dev.langchain4j.model.router;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatModelStreamingEvent;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteResponse;
import dev.langchain4j.model.chat.response.PartialResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StreamingModelRouterTest {

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void invalidDemandInOnSubscribeShouldSignalError(long n) {
        ControlledStreamingModel model = new ControlledStreamingModel();
        ControlledStreamingModel unused = new ControlledStreamingModel();
        AtomicInteger routingCalls = new AtomicInteger();
        StreamingModelRouter router = StreamingModelRouter.builder()
                .addRoutes(model, unused)
                .routingStrategy((models, request) -> models.get(routingCalls.getAndIncrement()))
                .build();
        DemandCollector collector = new DemandCollector(n);

        assertDoesNotThrow(() -> router.chat(REQUEST).subscribe(collector));

        assertInvalidDemandError(collector);
        assertEquals(0, routingCalls.get());
        assertEquals(0, model.calls);
        assertEquals(0, unused.calls);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void invalidDemandAfterSubscribeShouldCancelUpstreamAndIgnoreLateSignals(long n) {
        ControlledStreamingModel model = new ControlledStreamingModel();
        ControlledStreamingModel unused = new ControlledStreamingModel();
        StreamingModelRouter router = StreamingModelRouter.builder()
                .addRoutes(model, unused)
                .routingStrategy(roundRobinStrategy())
                .build();
        DemandCollector collector = new DemandCollector(null);
        router.chat(REQUEST).subscribe(collector);
        collector.subscription.request(1);
        // Cancellation may synchronously trigger a final upstream callback.
        model.onCancel = () -> model.subscriber.onError(new IllegalStateException("cancel callback"));

        assertDoesNotThrow(() -> collector.subscription.request(n));

        assertInvalidDemandError(collector);
        assertEquals(1, model.cancels.get());
        assertDoesNotThrow(() -> {
            collector.subscription.request(0);
            collector.subscription.request(-1);
            collector.subscription.request(1);
            collector.subscription.cancel();
        });
        // Already in-flight signals must not be forwarded after our onError.
        model.subscriber.onNext(new PartialResponse("late"));
        model.subscriber.onError(new IllegalStateException("late failure"));
        model.subscriber.onComplete();

        assertInvalidDemandError(collector);
        assertEquals(List.of(1L), model.requests);
        assertEquals(1, model.cancels.get());
        assertEquals(1, model.calls);
        assertEquals(0, unused.calls);
    }

    @Test
    void invalidDemandShouldCancelAnUpstreamThatRegistersLater() {
        ControlledStreamingModel model = new ControlledStreamingModel();
        model.registerImmediately = false;
        DemandCollector collector = new DemandCollector(null);
        StreamingModelRouter.builder()
                .addRoutes(model)
                .routingStrategy(fixedIndexStrategy(0))
                .build()
                .chat(REQUEST)
                .subscribe(collector);

        assertDoesNotThrow(() -> collector.subscription.request(0));
        model.register();

        assertInvalidDemandError(collector);
        assertEquals(1, model.cancels.get());
        assertTrue(model.requests.isEmpty());
    }

    @Test
    void invalidDemandDuringRouteSelectionShouldNotInvokeTheModel() {
        ControlledStreamingModel model = new ControlledStreamingModel();
        DemandCollector collector = new DemandCollector(null);
        StreamingModelRouter router = StreamingModelRouter.builder()
                .addRoutes(model)
                .routingStrategy((models, request) -> {
                    collector.subscription.request(0);
                    return models.get(0);
                })
                .build();

        assertDoesNotThrow(() -> router.chat(REQUEST).subscribe(collector));

        assertInvalidDemandError(collector);
        assertEquals(0, model.calls);
    }

    @Test
    void invalidDemandInOnNextShouldSignalErrorAfterTheCallbackReturns() {
        ControlledStreamingModel model = new ControlledStreamingModel();
        DemandCollector collector = new DemandCollector(1L);
        collector.onEvent = event -> collector.subscription.request(0);
        StreamingModelRouter.builder()
                .addRoutes(model)
                .routingStrategy(fixedIndexStrategy(0))
                .build()
                .chat(REQUEST)
                .subscribe(collector);

        assertDoesNotThrow(() -> model.subscriber.onNext(new PartialResponse("one")));

        assertEquals(List.of("onNext start", "onNext end", "onError"), collector.signals);
        assertEquals(1, collector.errors.size());
        assertInstanceOf(IllegalArgumentException.class, collector.errors.get(0));
        assertEquals(1, model.cancels.get());
    }

    @Test
    void invalidDemandOnAnotherThreadShouldWaitForOnNextBeforeSignallingError() throws Exception {
        ControlledStreamingModel model = new ControlledStreamingModel();
        DemandCollector collector = new DemandCollector(1L);
        CountDownLatch enteredOnNext = new CountDownLatch(1);
        CountDownLatch releaseOnNext = new CountDownLatch(1);
        collector.onEvent = event -> {
            enteredOnNext.countDown();
            try {
                if (!releaseOnNext.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("onNext was not released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        };
        StreamingModelRouter.builder()
                .addRoutes(model)
                .routingStrategy(fixedIndexStrategy(0))
                .build()
                .chat(REQUEST)
                .subscribe(collector);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> emission = executor.submit(() -> model.subscriber.onNext(new PartialResponse("one")));
            assertTrue(enteredOnNext.await(5, TimeUnit.SECONDS));
            // request must return without waiting for the subscriber's onNext to finish.
            executor.submit(() -> collector.subscription.request(0)).get(5, TimeUnit.SECONDS);
            assertTrue(collector.errors.isEmpty());
            assertEquals(1, model.cancels.get());
            releaseOnNext.countDown();
            emission.get(5, TimeUnit.SECONDS);
            assertEquals(List.of("onNext start", "onNext end", "onError"), collector.signals);
            assertEquals(1, collector.errors.size());
            assertInstanceOf(IllegalArgumentException.class, collector.errors.get(0));
        } finally {
            releaseOnNext.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancel", "complete", "error"})
    void requestsAfterCancellationOrTerminationShouldBeIgnored(String termination) {
        ControlledStreamingModel model = new ControlledStreamingModel();
        DemandCollector collector = new DemandCollector(null);
        StreamingModelRouter.builder()
                .addRoutes(model)
                .routingStrategy(fixedIndexStrategy(0))
                .build()
                .chat(REQUEST)
                .subscribe(collector);
        switch (termination) {
            case "cancel" -> collector.subscription.cancel();
            case "complete" -> model.subscriber.onComplete();
            case "error" -> model.subscriber.onError(new IllegalStateException("model failure"));
            default -> throw new AssertionError(termination);
        }

        assertDoesNotThrow(() -> {
            collector.subscription.request(0);
            collector.subscription.request(-1);
            collector.subscription.request(1);
            collector.subscription.cancel();
        });

        assertTrue(model.requests.isEmpty());
        assertEquals(termination.equals("cancel") ? 1 : 0, model.cancels.get());
        assertEquals(termination.equals("error") ? 1 : 0, collector.errors.size());
        assertEquals(termination.equals("complete") ? 1 : 0, collector.completions);
        assertTrue(collector.events.isEmpty());
    }

    @Test
    void positiveDemandShouldBeForwardedAcrossFailover() {
        ControlledStreamingModel first = new ControlledStreamingModel();
        ControlledStreamingModel second = new ControlledStreamingModel();
        DemandCollector collector = new DemandCollector(2L);
        StreamingModelRouter.builder()
                .addRoutes(first, second)
                .routingStrategy(roundRobinStrategy())
                .build()
                .chat(REQUEST)
                .subscribe(collector);
        assertEquals(List.of(2L), first.requests);

        first.subscriber.onError(new IllegalStateException("try next"));
        assertEquals(List.of(2L), second.requests);
        second.subscriber.onNext(new PartialResponse("one"));
        collector.subscription.request(3);
        assertEquals(List.of(2L, 3L), second.requests);
        second.subscriber.onNext(new PartialResponse("two"));
        second.subscriber.onComplete();

        assertEquals(2, collector.events.size());
        assertEquals(1, collector.completions);
        assertTrue(collector.errors.isEmpty());
        assertEquals(0, first.cancels.get());
        assertEquals(0, second.cancels.get());
    }

    private static void assertInvalidDemandError(DemandCollector collector) {
        assertEquals(1, collector.errors.size());
        assertInstanceOf(IllegalArgumentException.class, collector.errors.get(0));
        assertTrue(collector.events.isEmpty());
        assertEquals(0, collector.completions);
    }

    /** A manually driven upstream, including deliberately late signals to test termination. */
    private static final class ControlledStreamingModel implements StreamingChatModel {
        private Subscriber<? super ChatModelStreamingEvent> subscriber;
        private final List<Long> requests = new CopyOnWriteArrayList<>();
        private final AtomicInteger cancels = new AtomicInteger();
        private boolean registerImmediately = true;
        private Runnable onCancel = () -> {};
        private int calls;

        @Override
        public Publisher<ChatModelStreamingEvent> doChat(ChatRequest request) {
            calls++;
            return downstream -> {
                subscriber = downstream;
                if (registerImmediately) {
                    register();
                }
            };
        }

        private void register() {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    requests.add(n);
                }

                @Override
                public void cancel() {
                    cancels.incrementAndGet();
                    onCancel.run();
                }
            });
        }
    }

    private static final class DemandCollector implements Subscriber<ChatModelStreamingEvent> {
        private final Long initialDemand;
        private final List<Throwable> errors = new CopyOnWriteArrayList<>();
        private final List<ChatModelStreamingEvent> events = new CopyOnWriteArrayList<>();
        private final List<String> signals = new CopyOnWriteArrayList<>();
        private Consumer<ChatModelStreamingEvent> onEvent = event -> {};
        private Subscription subscription;
        private int completions;

        private DemandCollector(Long initialDemand) {
            this.initialDemand = initialDemand;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            this.subscription = subscription;
            if (initialDemand != null) {
                subscription.request(initialDemand);
            }
        }

        @Override
        public void onNext(ChatModelStreamingEvent event) {
            signals.add("onNext start");
            events.add(event);
            onEvent.accept(event);
            signals.add("onNext end");
        }

        @Override
        public void onError(Throwable error) {
            signals.add("onError");
            errors.add(error);
        }

        @Override
        public void onComplete() {
            completions++;
        }
    }

    private static final ChatRequest REQUEST =
            ChatRequest.builder().messages(new UserMessage("ping")).build();

    /**
     * A {@link StreamingChatModel} whose reactive and handler-based behaviors are scripted by the
     * tests, and which counts how many times each entry point is invoked.
     */
    private static class ScriptedStreamingModel implements StreamingChatModel {

        private final Consumer<Subscriber<? super ChatModelStreamingEvent>> reactiveScript;
        private final Consumer<StreamingChatResponseHandler> handlerScript;
        private final AtomicInteger reactiveCalls = new AtomicInteger();
        private final AtomicInteger handlerCalls = new AtomicInteger();

        ScriptedStreamingModel(
                Consumer<Subscriber<? super ChatModelStreamingEvent>> reactiveScript,
                Consumer<StreamingChatResponseHandler> handlerScript) {
            this.reactiveScript = reactiveScript;
            this.handlerScript = handlerScript;
        }

        @Override
        public Publisher<ChatModelStreamingEvent> doChat(ChatRequest chatRequest) {
            reactiveCalls.incrementAndGet();
            return subscriber -> {
                subscriber.onSubscribe(noOpSubscription());
                reactiveScript.accept(subscriber);
            };
        }

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            handlerCalls.incrementAndGet();
            if (handlerScript == null) {
                throw new UnsupportedOperationException("handler-based calls are not scripted for this model");
            }
            handlerScript.accept(handler);
        }

        int reactiveCalls() {
            return reactiveCalls.get();
        }

        int handlerCalls() {
            return handlerCalls.get();
        }
    }

    private static ScriptedStreamingModel successfulModel(String id) {
        return new ScriptedStreamingModel(
                subscriber -> {
                    subscriber.onNext(new PartialResponse(id));
                    subscriber.onNext(new CompleteResponse(response(id)));
                    subscriber.onComplete();
                },
                handler -> handler.onCompleteResponse(response(id)));
    }

    private static ScriptedStreamingModel immediatelyFailingModel(String message) {
        return new ScriptedStreamingModel(subscriber -> subscriber.onError(new IllegalStateException(message)), null);
    }

    private static ChatResponse response(String text) {
        return ChatResponse.builder().aiMessage(new AiMessage(text)).build();
    }

    private static Subscription noOpSubscription() {
        return new Subscription() {
            @Override
            public void request(long n) {}

            @Override
            public void cancel() {}
        };
    }

    /**
     * A strategy that always picks the given index, regardless of wrapper state.
     */
    private static ModelRoutingStrategy fixedIndexStrategy(int index) {
        return (availableModels, chatRequest) -> availableModels.get(index);
    }

    /**
     * A strategy that cycles through the available models on every call.
     */
    private static ModelRoutingStrategy roundRobinStrategy() {
        AtomicInteger nextIndex = new AtomicInteger();
        return (availableModels, chatRequest) ->
                availableModels.get(nextIndex.getAndIncrement() % availableModels.size());
    }

    private static final class EventCollector implements Subscriber<ChatModelStreamingEvent> {

        private final List<ChatModelStreamingEvent> events = new CopyOnWriteArrayList<>();
        private final AtomicReference<Throwable> error = new AtomicReference<>();
        private final CountDownLatch terminal = new CountDownLatch(1);

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ChatModelStreamingEvent event) {
            events.add(event);
        }

        @Override
        public void onError(Throwable throwable) {
            error.set(throwable);
            terminal.countDown();
        }

        @Override
        public void onComplete() {
            terminal.countDown();
        }

        void awaitTerminal() throws InterruptedException {
            assertTrue(terminal.await(10, TimeUnit.SECONDS), "the stream did not terminate in time");
        }
    }

    private static final class HandlerCollector implements StreamingChatResponseHandler {

        private final AtomicReference<ChatResponse> completeResponse = new AtomicReference<>();
        private final AtomicReference<Throwable> error = new AtomicReference<>();
        private final CountDownLatch terminal = new CountDownLatch(1);

        @Override
        public void onCompleteResponse(ChatResponse response) {
            completeResponse.set(response);
            terminal.countDown();
        }

        @Override
        public void onError(Throwable error) {
            this.error.set(error);
            terminal.countDown();
        }

        void awaitTerminal() throws InterruptedException {
            assertTrue(terminal.await(10, TimeUnit.SECONDS), "the stream did not terminate in time");
        }
    }

    /**
     * A successful reactive call is routed to the selected delegate and its events are passed
     * through unchanged.
     */
    @Test
    void successfulReactiveCallDelegatesToRoute() throws InterruptedException {
        ScriptedStreamingModel model = successfulModel("ok");
        StreamingModelRouter router = StreamingModelRouter.builder()
                .addRoutes(model)
                .routingStrategy(new LowestTokenUsageRoutingStrategy())
                .build();

        EventCollector collector = new EventCollector();
        router.chat(REQUEST).subscribe(collector);
        collector.awaitTerminal();

        assertEquals(2, collector.events.size());
        assertEquals("ok", ((PartialResponse) collector.events.get(0)).text());
        assertInstanceOf(CompleteResponse.class, collector.events.get(1));
        assertNull(collector.error.get());
        assertEquals(1, model.reactiveCalls());
    }

    /**
     * With a {@link FailoverStrategy}, a route failing before emitting any event is skipped and
     * the subscription is retried on the next healthy route.
     */
    @Test
    void reactiveCallFailsOverToHealthyRoute() throws InterruptedException {
        ScriptedStreamingModel failing = immediatelyFailingModel("boom");
        ScriptedStreamingModel healthy = successfulModel("healthy");
        StreamingModelRouter router = StreamingModelRouter.builder()
                .addRoutes(failing, healthy)
                .routingStrategy(new FailoverStrategy())
                .build();

        EventCollector collector = new EventCollector();
        router.chat(REQUEST).subscribe(collector);
        collector.awaitTerminal();

        assertNull(collector.error.get());
        assertEquals("healthy", ((PartialResponse) collector.events.get(0)).text());
        assertEquals(1, failing.reactiveCalls());
        assertEquals(1, healthy.reactiveCalls());
    }

    /**
     * Once events have been emitted downstream, a failure is propagated instead of being retried,
     * because retrying would duplicate or lose already-delivered events.
     */
    @Test
    void reactiveCallDoesNotRetryAfterEventsWereEmitted() throws InterruptedException {
        ScriptedStreamingModel partialThenFailing = new ScriptedStreamingModel(
                subscriber -> {
                    subscriber.onNext(new PartialResponse("partial"));
                    subscriber.onError(new IllegalStateException("mid-stream failure"));
                },
                null);
        ScriptedStreamingModel healthy = successfulModel("healthy");
        StreamingModelRouter router = StreamingModelRouter.builder()
                .addRoutes(partialThenFailing, healthy)
                .routingStrategy(new LowestTokenUsageRoutingStrategy())
                .build();

        EventCollector collector = new EventCollector();
        router.chat(REQUEST).subscribe(collector);
        collector.awaitTerminal();

        assertEquals(1, collector.events.size());
        assertEquals("partial", ((PartialResponse) collector.events.get(0)).text());
        Throwable error = collector.error.get();
        assertInstanceOf(IllegalStateException.class, error);
        assertEquals("mid-stream failure", error.getMessage());
        assertEquals(0, healthy.reactiveCalls());
    }

    /**
     * When the strategy keeps selecting the same failing route, the number of attempts is bounded
     * by the number of configured routes and the last failure surfaces.
     */
    @Test
    void reactiveCallBoundedByRouteCountWhenStrategyKeepsSelectingSameFailingModel() throws InterruptedException {
        ScriptedStreamingModel failing = immediatelyFailingModel("flaky");
        ScriptedStreamingModel unused = successfulModel("unused");
        StreamingModelRouter router = StreamingModelRouter.builder()
                .addRoutes(failing, unused)
                .routingStrategy(fixedIndexStrategy(0))
                .build();

        EventCollector collector = new EventCollector();
        router.chat(REQUEST).subscribe(collector);
        collector.awaitTerminal();

        Throwable error = collector.error.get();
        assertInstanceOf(IllegalStateException.class, error);
        assertEquals("flaky", error.getMessage());
        assertEquals(2, failing.reactiveCalls());
        assertEquals(0, unused.reactiveCalls());
    }

    /**
     * A single failing route surfaces its error directly without retrying.
     */
    @Test
    void reactiveCallPropagatesFailureOfSingleRoute() throws InterruptedException {
        ScriptedStreamingModel failing = immediatelyFailingModel("permanent delegate failure");
        StreamingModelRouter router = StreamingModelRouter.builder()
                .addRoutes(failing)
                .routingStrategy(new LowestTokenUsageRoutingStrategy())
                .build();

        EventCollector collector = new EventCollector();
        router.chat(REQUEST).subscribe(collector);
        collector.awaitTerminal();

        Throwable error = collector.error.get();
        assertInstanceOf(IllegalStateException.class, error);
        assertEquals("permanent delegate failure", error.getMessage());
        assertEquals(1, failing.reactiveCalls());
    }

    /**
     * When no route matches and a default route is configured, reactive calls are delegated to it.
     */
    @Test
    void reactiveCallFallsBackToDefaultRoute() throws InterruptedException {
        ScriptedStreamingModel defaultModel = successfulModel("default");
        StreamingModelRouter router = StreamingModelRouter.builder()
                .addRoutes(immediatelyFailingModel("ignored"))
                .defaultRoute(defaultModel)
                .routingStrategy((availableModels, chatRequest) -> null)
                .build();

        EventCollector collector = new EventCollector();
        router.chat(REQUEST).subscribe(collector);
        collector.awaitTerminal();

        assertNull(collector.error.get());
        assertEquals("default", ((PartialResponse) collector.events.get(0)).text());
        assertEquals(1, defaultModel.reactiveCalls());
    }

    /**
     * When no route matches and no default route is configured, the subscriber receives an error.
     */
    @Test
    void reactiveCallFailsWhenNoRouteMatches() throws InterruptedException {
        StreamingModelRouter router = StreamingModelRouter.builder()
                .addRoutes(successfulModel("unused"))
                .routingStrategy((availableModels, chatRequest) -> null)
                .build();

        EventCollector collector = new EventCollector();
        router.chat(REQUEST).subscribe(collector);
        collector.awaitTerminal();

        Throwable error = collector.error.get();
        assertInstanceOf(IllegalStateException.class, error);
        assertEquals("No matching route for request found", error.getMessage());
    }

    /**
     * The handler-based entry point fails over when a delegate throws synchronously, mirroring the
     * synchronous router behavior.
     */
    @Test
    void handlerBasedCallFailsOverOnSynchronousFailure() throws InterruptedException {
        ScriptedStreamingModel throwing = new ScriptedStreamingModel(null, handler -> {
            throw new IllegalStateException("sync failure");
        });
        ScriptedStreamingModel healthy = successfulModel("healthy");
        StreamingModelRouter router = StreamingModelRouter.builder()
                .addRoutes(throwing, healthy)
                .routingStrategy(roundRobinStrategy())
                .build();

        HandlerCollector collector = new HandlerCollector();
        router.chat(REQUEST, collector);
        collector.awaitTerminal();

        assertNull(collector.error.get());
        assertEquals("healthy", collector.completeResponse.get().aiMessage().text());
        assertEquals(1, throwing.handlerCalls());
        assertEquals(1, healthy.handlerCalls());
    }
}
