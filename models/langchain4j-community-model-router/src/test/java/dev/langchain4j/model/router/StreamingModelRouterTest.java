package dev.langchain4j.model.router;

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
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class StreamingModelRouterTest {

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
