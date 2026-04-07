package dev.langchain4j.community.model.oracle.oci.genai;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceAsyncClient;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import com.oracle.bmc.responses.AsyncHandler;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

class OciGenAiStreamingChatModelTest {

    private static final long WAIT_TIMEOUT_SECONDS = 10;

    private static final String STREAMED_DATA = """
            data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":"HELLO"}]}}
            data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":" WORLD"}]}}
            data: {"finishReason":"stop"}
            """;

    @Test
    void doChatUsesAsyncClientWithoutBlockingCaller() throws Exception {
        var syncClient = mock(GenerativeAiInferenceClient.class);
        var asyncClient = mock(GenerativeAiInferenceAsyncClient.class);
        var requestScheduled = new CountDownLatch(1);
        var releaseResponse = new CountDownLatch(1);

        doAnswer(invocation -> {
                    var request =
                            invocation.getArgument(0, com.oracle.bmc.generativeaiinference.requests.ChatRequest.class);
                    @SuppressWarnings("unchecked")
                    AsyncHandler<
                                    com.oracle.bmc.generativeaiinference.requests.ChatRequest,
                                    com.oracle.bmc.generativeaiinference.responses.ChatResponse>
                            asyncHandler = invocation.getArgument(1, AsyncHandler.class);

                    var future = new CompletableFuture<com.oracle.bmc.generativeaiinference.responses.ChatResponse>();
                    var worker = new Thread(() -> {
                        requestScheduled.countDown();
                        try {
                            assertTrue(releaseResponse.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                            var response = ociResponse();
                            asyncHandler.onSuccess(request, response);
                            future.complete(response);
                        } catch (Throwable t) {
                            asyncHandler.onError(request, t);
                            future.completeExceptionally(t);
                        }
                    });
                    worker.setDaemon(true);
                    worker.start();
                    return future;
                })
                .when(asyncClient)
                .chat(any(com.oracle.bmc.generativeaiinference.requests.ChatRequest.class), any());

        var handler = new TestStreamingChatResponseHandler();
        try (var model = OciGenAiStreamingChatModel.builder()
                .modelName("test-model")
                .compartmentId("test-compartment")
                .genAiClient(syncClient)
                .genAiAsyncClient(asyncClient)
                .build()) {

            model.doChat(chatRequest(), handler);

            assertTrue(requestScheduled.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertTrue(handler.partialResponses.isEmpty());

            releaseResponse.countDown();

            assertTrue(handler.completed.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertNull(handler.error.get());
            assertThat(handler.partialResponses, contains("HELLO", " WORLD"));

            verify(asyncClient).chat(any(com.oracle.bmc.generativeaiinference.requests.ChatRequest.class), any());
            verify(syncClient, never()).chat(any(com.oracle.bmc.generativeaiinference.requests.ChatRequest.class));
        }
    }

    @Test
    void doChatOffloadsSyncClientWhenAsyncClientIsUnavailable() throws Exception {
        var syncClient = mock(GenerativeAiInferenceClient.class);
        var requestStarted = new CountDownLatch(1);
        var releaseResponse = new CountDownLatch(1);

        doAnswer(invocation -> {
                    requestStarted.countDown();
                    assertTrue(releaseResponse.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                    return ociResponse();
                })
                .when(syncClient)
                .chat(any(com.oracle.bmc.generativeaiinference.requests.ChatRequest.class));

        var handler = new TestStreamingChatResponseHandler();
        try (var model = OciGenAiStreamingChatModel.builder()
                .modelName("test-model")
                .compartmentId("test-compartment")
                .genAiClient(syncClient)
                .build()) {

            model.doChat(chatRequest(), handler);

            assertTrue(requestStarted.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertTrue(handler.partialResponses.isEmpty());

            releaseResponse.countDown();

            assertTrue(handler.completed.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertNull(handler.error.get());
            assertThat(handler.partialResponses, contains("HELLO", " WORLD"));

            verify(syncClient).chat(any(com.oracle.bmc.generativeaiinference.requests.ChatRequest.class));
        }
    }

    @Test
    void doChatRoutesAsyncStartupFailuresToHandler() throws Exception {
        var asyncClient = mock(GenerativeAiInferenceAsyncClient.class);
        var failure = new IllegalStateException("boom");

        doAnswer(invocation -> {
                    throw failure;
                })
                .when(asyncClient)
                .chat(any(com.oracle.bmc.generativeaiinference.requests.ChatRequest.class), any());

        var handler = new TestStreamingChatResponseHandler();
        try (var model = OciGenAiStreamingChatModel.builder()
                .modelName("test-model")
                .compartmentId("test-compartment")
                .genAiAsyncClient(asyncClient)
                .build()) {

            model.doChat(chatRequest(), handler);

            assertTrue(handler.completed.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertSame(failure, handler.error.get());
        }
    }

    @Test
    void directHandlerReproducerStreamsBeforeCompletion() throws Exception {
        var streamScript = new BlockingEventStream();

        try (var model = modelWithAsyncResponse(streamScript.response())) {
            var handler = new TestStreamingChatResponseHandler();

            CompletableFuture<Void> invokeFuture = CompletableFuture.runAsync(() -> model.chat(chatRequest(), handler));
            invokeFuture.get(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertTrue(handler.firstPartial.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertThat(handler.partialResponses, contains("HELLO"));
            assertFalse(handler.completed.await(200, TimeUnit.MILLISECONDS));

            streamScript.allowCompletion();

            assertTrue(handler.completed.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            streamScript.assertCompleted();
            assertNull(handler.error.get());
            assertThat(handler.partialResponses, contains("HELLO", " WORLD"));
        }
    }

    @Test
    void tokenStreamReproducerStreamsIntoSinkBeforeCompletion() throws Exception {
        var streamScript = new BlockingEventStream();

        try (var model = modelWithAsyncResponse(streamScript.response())) {
            TokenStreamAssistant assistant = AiServices.builder(TokenStreamAssistant.class)
                    .streamingChatModel(model)
                    .build();

            Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
            List<String> partialResponses = new CopyOnWriteArrayList<>();
            CountDownLatch firstPartial = new CountDownLatch(1);
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<Throwable> error = new AtomicReference<>();

            sink.asFlux()
                    .doOnNext(token -> {
                        partialResponses.add(token);
                        firstPartial.countDown();
                    })
                    .doOnComplete(completed::countDown)
                    .doOnError(error::set)
                    .subscribe();

            CompletableFuture<Void> startFuture = CompletableFuture.runAsync(() -> assistant
                    .chat("Hello")
                    .onPartialResponse(sink::tryEmitNext)
                    .onCompleteResponse(ignored -> sink.tryEmitComplete())
                    .onError(sink::tryEmitError)
                    .start());

            startFuture.get(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertTrue(firstPartial.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertThat(partialResponses, contains("HELLO"));
            assertFalse(completed.await(200, TimeUnit.MILLISECONDS));

            streamScript.allowCompletion();

            assertTrue(completed.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            streamScript.assertCompleted();
            assertNull(error.get());
            assertThat(partialResponses, contains("HELLO", " WORLD"));
        }
    }

    @Test
    void fluxReproducerReturnsImmediatelyAndStreamsBeforeCompletion() throws Exception {
        var streamScript = new BlockingEventStream();

        try (var model = modelWithAsyncResponse(streamScript.response())) {
            FluxAssistant assistant = AiServices.builder(FluxAssistant.class)
                    .streamingChatModel(model)
                    .build();

            CompletableFuture<Flux<String>> fluxFuture = CompletableFuture.supplyAsync(() -> assistant.chat("Hello"));
            Flux<String> flux = fluxFuture.get(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            List<String> partialResponses = new CopyOnWriteArrayList<>();
            CountDownLatch firstPartial = new CountDownLatch(1);
            CountDownLatch completed = new CountDownLatch(1);
            AtomicReference<Throwable> error = new AtomicReference<>();

            flux.doOnNext(token -> {
                        partialResponses.add(token);
                        firstPartial.countDown();
                    })
                    .doOnComplete(completed::countDown)
                    .doOnError(error::set)
                    .subscribe();

            assertTrue(firstPartial.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertThat(partialResponses, contains("HELLO"));
            assertFalse(completed.await(200, TimeUnit.MILLISECONDS));

            streamScript.allowCompletion();

            assertTrue(completed.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            streamScript.assertCompleted();
            assertNull(error.get());
            assertThat(partialResponses, contains("HELLO", " WORLD"));
        }
    }

    @Test
    void tryWithResourcesShouldWaitForAsyncStreamCompletion() throws Exception {
        var streamScript = new BlockingEventStream();
        var handler = new TestStreamingChatResponseHandler();

        var invocation = CompletableFuture.runAsync(() -> {
            try (var model = modelWithAsyncResponse(streamScript.response())) {
                model.doChat(chatRequest(), handler);
            }
        });

        assertTrue(handler.firstPartial.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertFalse(invocation.isDone());
        assertFalse(handler.completed.await(200, TimeUnit.MILLISECONDS));

        streamScript.allowCompletion();

        invocation.get(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(handler.completed.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        streamScript.assertCompleted();
        assertNull(handler.error.get());
        assertThat(handler.partialResponses, contains("HELLO", " WORLD"));
    }

    @Test
    void tryWithResourcesShouldWaitForSyncFallbackStreamCompletion() throws Exception {
        var streamScript = new BlockingEventStream();
        var handler = new TestStreamingChatResponseHandler();

        var invocation = CompletableFuture.runAsync(() -> {
            try (var model = modelWithSyncResponse(streamScript.response())) {
                model.doChat(chatRequest(), handler);
            }
        });

        assertTrue(handler.firstPartial.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertFalse(invocation.isDone());
        assertFalse(handler.completed.await(200, TimeUnit.MILLISECONDS));

        streamScript.allowCompletion();

        invocation.get(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue(handler.completed.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        streamScript.assertCompleted();
        assertNull(handler.error.get());
        assertThat(handler.partialResponses, contains("HELLO", " WORLD"));
    }

    @Test
    void closeInsideStreamingCallbackShouldNotDeadlock() throws Exception {
        var streamScript = new BlockingEventStream();
        var model = modelWithAsyncResponse(streamScript.response());

        try {
            var closeInvoked = new AtomicBoolean();
            var closeReturned = new CountDownLatch(1);
            var completed = new CountDownLatch(1);
            var closeError = new AtomicReference<Throwable>();
            var streamError = new AtomicReference<Throwable>();

            model.doChat(chatRequest(), new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    if (closeInvoked.compareAndSet(false, true)) {
                        try {
                            model.close();
                        } catch (Throwable t) {
                            closeError.set(t);
                        } finally {
                            closeReturned.countDown();
                        }
                    }
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    completed.countDown();
                }

                @Override
                public void onError(Throwable throwable) {
                    streamError.set(throwable);
                    completed.countDown();
                }
            });

            assertTrue(closeReturned.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertNull(closeError.get());
            assertFalse(completed.await(200, TimeUnit.MILLISECONDS));

            streamScript.allowCompletion();

            assertTrue(completed.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            streamScript.assertCompleted();
            assertNull(streamError.get());
        } finally {
            model.close();
        }
    }

    private static ChatRequest chatRequest() {
        return ChatRequest.builder().messages(UserMessage.from("Hello")).build();
    }

    private static com.oracle.bmc.generativeaiinference.responses.ChatResponse ociResponse() {
        return com.oracle.bmc.generativeaiinference.responses.ChatResponse.builder()
                .eventStream(new ByteArrayInputStream(STREAMED_DATA.getBytes(UTF_8)))
                .build();
    }

    private static OciGenAiStreamingChatModel modelWithAsyncResponse(
            com.oracle.bmc.generativeaiinference.responses.ChatResponse response) {
        var asyncClient = mock(GenerativeAiInferenceAsyncClient.class);

        doAnswer(invocation -> {
                    var request =
                            invocation.getArgument(0, com.oracle.bmc.generativeaiinference.requests.ChatRequest.class);
                    @SuppressWarnings("unchecked")
                    AsyncHandler<
                                    com.oracle.bmc.generativeaiinference.requests.ChatRequest,
                                    com.oracle.bmc.generativeaiinference.responses.ChatResponse>
                            asyncHandler = invocation.getArgument(1, AsyncHandler.class);

                    var future = new CompletableFuture<com.oracle.bmc.generativeaiinference.responses.ChatResponse>();
                    var worker = new Thread(() -> {
                        try {
                            asyncHandler.onSuccess(request, response);
                            future.complete(response);
                        } catch (Throwable t) {
                            asyncHandler.onError(request, t);
                            future.completeExceptionally(t);
                        }
                    });
                    worker.setDaemon(true);
                    worker.start();
                    return future;
                })
                .when(asyncClient)
                .chat(any(com.oracle.bmc.generativeaiinference.requests.ChatRequest.class), any());

        return OciGenAiStreamingChatModel.builder()
                .modelName("test-model")
                .compartmentId("test-compartment")
                .genAiAsyncClient(asyncClient)
                .build();
    }

    private static OciGenAiStreamingChatModel modelWithSyncResponse(
            com.oracle.bmc.generativeaiinference.responses.ChatResponse response) {
        var syncClient = mock(GenerativeAiInferenceClient.class);

        doAnswer(invocation -> response)
                .when(syncClient)
                .chat(any(com.oracle.bmc.generativeaiinference.requests.ChatRequest.class));

        return OciGenAiStreamingChatModel.builder()
                .modelName("test-model")
                .compartmentId("test-compartment")
                .genAiClient(syncClient)
                .build();
    }

    private interface TokenStreamAssistant {
        TokenStream chat(String text);
    }

    private interface FluxAssistant {
        Flux<String> chat(String text);
    }

    private static class BlockingEventStream {

        private final CountDownLatch completionAllowed = new CountDownLatch(1);
        private final CountDownLatch writerDone = new CountDownLatch(1);
        private final AtomicReference<Throwable> writerError = new AtomicReference<>();
        private final com.oracle.bmc.generativeaiinference.responses.ChatResponse response =
                com.oracle.bmc.generativeaiinference.responses.ChatResponse.builder()
                        .eventStream(createEventStream())
                        .build();

        com.oracle.bmc.generativeaiinference.responses.ChatResponse response() {
            return response;
        }

        void allowCompletion() {
            completionAllowed.countDown();
        }

        void assertCompleted() throws InterruptedException {
            assertTrue(writerDone.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertNull(writerError.get());
        }

        private PipedInputStream createEventStream() {
            try {
                var input = new PipedInputStream();
                var output = new PipedOutputStream(input);
                var worker = new Thread(() -> writeStream(output));
                worker.setDaemon(true);
                worker.start();
                return input;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void writeStream(PipedOutputStream output) {
            try (output) {
                writeLine(
                        output,
                        "data: {\"index\":0,\"message\":{\"role\":\"ASSISTANT\",\"content\":[{\"type\":\"TEXT\",\"text\":\"HELLO\"}]}}");
                if (!completionAllowed.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to complete stream");
                }
                writeLine(
                        output,
                        "data: {\"index\":0,\"message\":{\"role\":\"ASSISTANT\",\"content\":[{\"type\":\"TEXT\",\"text\":\" WORLD\"}]}}");
                writeLine(output, "data: {\"finishReason\":\"stop\"}");
            } catch (Throwable t) {
                writerError.set(t);
            } finally {
                writerDone.countDown();
            }
        }

        private void writeLine(PipedOutputStream output, String line) throws IOException {
            output.write((line + "\n").getBytes(UTF_8));
            output.flush();
        }
    }

    private static class TestStreamingChatResponseHandler implements StreamingChatResponseHandler {

        private final List<String> partialResponses = new CopyOnWriteArrayList<>();
        private final CountDownLatch firstPartial = new CountDownLatch(1);
        private final CountDownLatch completed = new CountDownLatch(1);
        private final AtomicReference<Throwable> error = new AtomicReference<>();

        @Override
        public void onPartialResponse(String partialResponse) {
            partialResponses.add(partialResponse);
            firstPartial.countDown();
        }

        @Override
        public void onCompleteResponse(ChatResponse completeResponse) {
            completed.countDown();
        }

        @Override
        public void onError(Throwable throwable) {
            error.set(throwable);
            completed.countDown();
        }
    }
}
