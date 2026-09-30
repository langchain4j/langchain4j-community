package dev.langchain4j.model.router;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AsyncNotSupportedException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ModelRouterAsyncTest {
    @Test
    void routingErrorDuringAsyncFailoverCompletesFuture() {
        AsyncModel first = new AsyncModel();
        AsyncModel unused = new AsyncModel();
        AssertionError error = new AssertionError("routing failed");
        AtomicInteger selections = new AtomicInteger();
        CompletableFuture<ChatResponse> future = router(
                        (models, request) -> {
                            if (selections.getAndIncrement() == 0) {
                                return models.get(0);
                            }
                            throw error;
                        },
                        first,
                        unused)
                .chatAsync(REQUEST);
        first.response.completeExceptionally(new IllegalStateException("first failed"));
        assertTrue(future.isDone(), "A failure in a completion callback must not leave the result pending");
        assertSame(error, failure(future));
        assertEquals(0, unused.calls.get());
    }

    @Test
    void delegateErrorIsDeliveredWithoutRetry() {
        AssertionError error = new AssertionError("delegate failed");
        ChatModel first = new ChatModel() {
            @Override
            public CompletableFuture<ChatResponse> doChatAsync(ChatRequest request) {
                throw error;
            }
        };
        AsyncModel unused = new AsyncModel();
        CompletableFuture<ChatResponse> future = assertDoesNotThrow(
                () -> router(new FailoverStrategy(), first, unused).chatAsync(REQUEST));
        assertSame(error, failure(future));
        assertEquals(0, unused.calls.get());
    }

    @Test
    void asyncOptionsPreserveCallerAttributesWithoutMutatingThem() {
        Map<Object, Object> attributes = Map.of("request-id", "test");
        ChatRequestOptions options =
                ChatRequestOptions.builder().listenerAttributes(attributes).build();
        ChatModelWrapper wrapper = new ChatModelWrapper(new ChatModel() {});
        AtomicReference<Object> requestId = new AtomicReference<>();
        AtomicReference<Throwable> observedError = new AtomicReference<>();
        wrapper.addListener(new ChatModelListener() {
            @Override
            public void onRequest(ChatModelRequestContext context) {
                requestId.set(context.attributes().get("request-id"));
            }

            @Override
            public void onError(ChatModelErrorContext context) {
                observedError.set(context.error());
            }
        });
        Throwable error = failure(wrapper.chatAsync(REQUEST, options));
        assertInstanceOf(AsyncNotSupportedException.class, error);
        assertSame(error, observedError.get());
        assertEquals("test", requestId.get());
        assertEquals(attributes, options.listenerAttributes());
    }

    @Test
    void overlappingAsyncCallDoesNotSuppressSynchronousFailureTracking() {
        CompletableFuture<ChatResponse> pending = new CompletableFuture<>();
        AtomicInteger syncCalls = new AtomicInteger();
        ChatModel primary = new ChatModel() {
            @Override
            public CompletableFuture<ChatResponse> doChatAsync(ChatRequest request) {
                return pending;
            }

            @Override
            public ChatResponse doChat(ChatRequest request) {
                syncCalls.incrementAndGet();
                throw new AsyncNotSupportedException("sync failure");
            }
        };
        ChatModel healthy = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                return RESPONSE;
            }
        };
        ModelRouter router = router(new FailoverStrategy(), primary, healthy);
        CompletableFuture<ChatResponse> async = router.chatAsync(REQUEST);
        assertSame(RESPONSE, router.chat(REQUEST));
        pending.completeExceptionally(new AsyncNotSupportedException("async unsupported"));
        assertInstanceOf(AsyncNotSupportedException.class, failure(async));
        assertSame(RESPONSE, router.chat(REQUEST));
        assertEquals(1, syncCalls.get());
    }

    @Test
    void nullAsyncOptionsAreSupported() {
        AsyncModel model = new AsyncModel();
        model.response.complete(RESPONSE);
        assertSame(
                RESPONSE, new ChatModelWrapper(model).chatAsync(REQUEST, null).join());
    }

    private static final ChatRequest REQUEST =
            ChatRequest.builder().messages(UserMessage.from("ping")).build();
    private static final ChatResponse RESPONSE =
            ChatResponse.builder().aiMessage(AiMessage.from("pong")).build();

    private static ModelRouter router(ModelRoutingStrategy strategy, ChatModel... models) {
        return ModelRouter.builder().addRoutes(models).routingStrategy(strategy).build();
    }

    private static Throwable failure(CompletableFuture<?> future) {
        return assertThrows(CompletionException.class, future::join).getCause();
    }

    private static class AsyncModel implements ChatModel {
        final CompletableFuture<ChatResponse> response = new CompletableFuture<>();
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public CompletableFuture<ChatResponse> doChatAsync(ChatRequest request) {
            calls.incrementAndGet();
            assertEquals(REQUEST.messages(), request.messages());
            return response;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            throw new AssertionError("Blocking API must not be invoked");
        }
    }

    @Test
    void wrapperPreservesNativeAsyncSuccessAndCancellation() {
        AsyncModel model = new AsyncModel();
        CompletableFuture<ChatResponse> future = new ChatModelWrapper(model).chatAsync(REQUEST);
        assertFalse(future.isDone());
        model.response.complete(RESPONSE);
        assertSame(RESPONSE, future.join());
        assertEquals(1, model.calls.get());

        AsyncModel cancellable = new AsyncModel();
        assertTrue(new ChatModelWrapper(cancellable).chatAsync(REQUEST).cancel(true));
        assertTrue(cancellable.response.isCancelled());
    }

    @Test
    void wrapperPreservesFailure() {
        AsyncModel model = new AsyncModel();
        CompletableFuture<ChatResponse> future = new ChatModelWrapper(model).chatAsync(REQUEST);
        IllegalStateException error = new IllegalStateException("delegate");
        model.response.completeExceptionally(error);
        assertSame(error, failure(future));
    }

    @Test
    void routerReturnsBeforeDelegateCompletes() throws Exception {
        AsyncModel model = new AsyncModel();
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            CompletableFuture<ChatResponse> future = caller.submit(
                            () -> router(new FailoverStrategy(), model).chatAsync(REQUEST))
                    .get(5, TimeUnit.SECONDS);
            assertFalse(future.isDone());
            model.response.complete(RESPONSE);
            assertSame(RESPONSE, future.get(5, TimeUnit.SECONDS));
        } finally {
            caller.shutdownNow();
        }
    }

    @Test
    void asynchronousFailureSelectsHealthyRoute() {
        AsyncModel first = new AsyncModel();
        AsyncModel second = new AsyncModel();
        CompletableFuture<ChatResponse> future =
                router(new FailoverStrategy(), first, second).chatAsync(REQUEST);
        assertEquals(0, second.calls.get());
        first.response.completeExceptionally(new IllegalStateException("first failed"));
        assertEquals(1, second.calls.get());
        assertFalse(future.isDone());
        second.response.complete(RESPONSE);
        assertSame(RESPONSE, future.join());
    }

    @Test
    void lastFailureIsPreserved() {
        AsyncModel first = new AsyncModel();
        AsyncModel second = new AsyncModel();
        CompletableFuture<ChatResponse> future =
                router(new FailoverStrategy(), first, second).chatAsync(REQUEST);
        first.response.completeExceptionally(new IllegalStateException("first"));
        IllegalArgumentException last = new IllegalArgumentException("last");
        second.response.completeExceptionally(new CompletionException(last));
        assertSame(last, failure(future));
    }

    @Test
    void immediatelyFailedAttemptsAreBoundedWithoutRecursiveStackGrowth() {
        AsyncModel model = new AsyncModel();
        IllegalStateException error = new IllegalStateException("failed");
        model.response.completeExceptionally(error);
        ChatModel[] routes = new ChatModel[5000];
        java.util.Arrays.fill(routes, model);
        CompletableFuture<ChatResponse> future =
                router((models, request) -> models.get(0), routes).chatAsync(REQUEST);
        assertSame(error, failure(future));
        assertEquals(routes.length, model.calls.get());
    }

    @Test
    void unsupportedAsyncDoesNotDisableSynchronousRouteOrInvokeAnotherRoute() {
        AtomicInteger syncCalls = new AtomicInteger();
        ChatModel blocking = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest request) {
                syncCalls.incrementAndGet();
                return RESPONSE;
            }
        };
        AsyncModel unused = new AsyncModel();
        ModelRouter router = router(new FailoverStrategy(), blocking, unused);
        assertInstanceOf(AsyncNotSupportedException.class, failure(router.chatAsync(REQUEST)));
        assertEquals(0, syncCalls.get());
        assertEquals(0, unused.calls.get());
        assertSame(RESPONSE, router.chat(REQUEST));
        assertEquals(1, syncCalls.get());
    }

    @Test
    void cancellationReachesDelegateWithoutFailover() {
        AsyncModel first = new AsyncModel();
        AsyncModel second = new AsyncModel();
        CompletableFuture<ChatResponse> future =
                router(new FailoverStrategy(), first, second).chatAsync(REQUEST);
        assertTrue(future.cancel(true));
        assertTrue(first.response.isCancelled());
        assertEquals(0, second.calls.get());
        assertFalse(first.response.complete(RESPONSE));
        assertTrue(future.isCancelled());
    }

    @Test
    void cancellationAfterFailoverReachesNewDelegate() {
        AsyncModel first = new AsyncModel();
        AsyncModel second = new AsyncModel();
        CompletableFuture<ChatResponse> future =
                router(new FailoverStrategy(), first, second).chatAsync(REQUEST);
        first.response.completeExceptionally(new IllegalStateException("first"));
        future.cancel(true);
        assertTrue(second.response.isCancelled());
    }

    @Test
    void delegateCancellationDoesNotFailOver() {
        AsyncModel first = new AsyncModel();
        AsyncModel second = new AsyncModel();
        CompletableFuture<ChatResponse> future =
                router(new FailoverStrategy(), first, second).chatAsync(REQUEST);
        first.response.cancel(true);
        assertInstanceOf(CancellationException.class, failure(future));
        assertEquals(0, second.calls.get());
    }

    @Test
    void defaultRouteSupportsAsync() {
        AsyncModel model = new AsyncModel();
        CompletableFuture<ChatResponse> future = ModelRouter.builder()
                .routingStrategy((models, request) -> null)
                .defaultRoute(model)
                .build()
                .chatAsync(REQUEST);
        model.response.complete(RESPONSE);
        assertSame(RESPONSE, future.join());
    }

    @Test
    void routingFailureIsDeliveredThroughFuture() {
        IllegalStateException error = new IllegalStateException("routing");
        CompletableFuture<ChatResponse> future = assertDoesNotThrow(() -> router((models, request) -> {
                    throw error;
                })
                .doChatAsync(REQUEST));
        assertSame(error, failure(future));
    }

    @Test
    void synchronousDelegateThrowCanFailOver() {
        ChatModel throwing = new ChatModel() {
            @Override
            public CompletableFuture<ChatResponse> doChatAsync(ChatRequest request) {
                throw new IllegalArgumentException("validation");
            }
        };
        AsyncModel second = new AsyncModel();
        second.response.complete(RESPONSE);
        assertSame(
                RESPONSE,
                router(new FailoverStrategy(), throwing, second)
                        .chatAsync(REQUEST)
                        .join());
    }

    @Test
    void noMatchingRouteFailsThroughFuture() {
        CompletableFuture<ChatResponse> future =
                assertDoesNotThrow(() -> router((models, request) -> null).chatAsync(REQUEST));
        assertEquals("No matching route for request found", failure(future).getMessage());
    }

    @Test
    void noMatchingModelExceptionDoesNotRetry() {
        AsyncModel first = new AsyncModel();
        AsyncModel unused = new AsyncModel();
        NoMatchingModelFoundException error = new NoMatchingModelFoundException("no model");
        first.response.completeExceptionally(error);
        assertSame(error, failure(router(new FailoverStrategy(), first, unused).chatAsync(REQUEST)));
        assertEquals(0, unused.calls.get());
    }

    @Test
    void cancelledRequestDoesNotPutRouteInCooldown() {
        AsyncModel model = new AsyncModel();
        ModelRouter router = router(new FailoverStrategy(), model);
        router.chatAsync(REQUEST).cancel(true);
        router.chatAsync(REQUEST);
        assertEquals(2, model.calls.get());
    }

    @Test
    void singleUnsupportedDelegatePreservesExceptionType() {
        ChatModel unsupported = new ChatModel() {};
        assertInstanceOf(
                AsyncNotSupportedException.class, failure(new ChatModelWrapper(unsupported).chatAsync(REQUEST)));
        assertInstanceOf(
                AsyncNotSupportedException.class,
                failure(router(new FailoverStrategy(), unsupported).chatAsync(REQUEST)));
    }

    @Test
    void cancellationDuringRouteSelectionPreventsDelegateInvocation() throws Exception {
        CountDownLatch selecting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger selections = new AtomicInteger();
        AsyncModel first = new AsyncModel();
        AsyncModel second = new AsyncModel();
        CompletableFuture<ChatResponse> future = router(
                        (models, request) -> {
                            if (selections.getAndIncrement() == 0) {
                                return models.get(0);
                            }
                            selecting.countDown();
                            await(release);
                            return models.get(1);
                        },
                        first,
                        second)
                .chatAsync(REQUEST);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            var task = worker.submit(() -> first.response.completeExceptionally(new IllegalStateException("first")));
            assertTrue(selecting.await(5, TimeUnit.SECONDS));
            future.cancel(true);
            release.countDown();
            task.get(5, TimeUnit.SECONDS);
            assertEquals(0, second.calls.get());
        } finally {
            release.countDown();
            worker.shutdownNow();
        }
    }

    @Test
    void cancellationBeforeDelegateReturnsFutureCancelsLateFuture() throws Exception {
        CountDownLatch invoking = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AsyncModel first = new AsyncModel();
        AsyncModel second = new AsyncModel() {
            @Override
            public CompletableFuture<ChatResponse> doChatAsync(ChatRequest request) {
                invoking.countDown();
                await(release);
                return super.doChatAsync(request);
            }
        };
        CompletableFuture<ChatResponse> future =
                router(new FailoverStrategy(), first, second).chatAsync(REQUEST);
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            var task = worker.submit(() -> first.response.completeExceptionally(new IllegalStateException("first")));
            assertTrue(invoking.await(5, TimeUnit.SECONDS));
            future.cancel(true);
            release.countDown();
            task.get(5, TimeUnit.SECONDS);
            assertTrue(second.response.isCancelled());
            assertTrue(future.isCancelled());
        } finally {
            release.countDown();
            worker.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError(error);
        }
    }
}
