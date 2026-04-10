package dev.langchain4j.community.model.oracle.oci.genai;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceAsyncClient;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OciGenAiCohereStreamingChatModelTest {

    private static final long WAIT_TIMEOUT_SECONDS = 10;

    private static final String STREAMED_DATA = """
            data: {"apiFormat":"COHERE","text":"HELLO"}
            data: {"apiFormat":"COHERE","text":" WORLD"}
            """;

    private static final String TOOL_CALL_DATA = """
            data: {"apiFormat":"COHERE","toolCalls":[{"name":"getWeather","parameters":{"city":"Munich"}}]}
            """;

    @Test
    void doChatUsesAsyncClientWithoutBlockingCaller() throws Exception {
        var syncClient = mock(GenerativeAiInferenceClient.class);
        var asyncClient = mock(GenerativeAiInferenceAsyncClient.class);
        var requestScheduled = new CountDownLatch(1);
        var releaseResponse = new CountDownLatch(1);

        doAnswer(invocation -> {
                    var future = new CompletableFuture<com.oracle.bmc.generativeaiinference.responses.ChatResponse>();
                    var worker = new Thread(() -> {
                        requestScheduled.countDown();
                        try {
                            assertTrue(releaseResponse.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                            future.complete(ociResponse(STREAMED_DATA));
                        } catch (Throwable t) {
                            future.completeExceptionally(t);
                        }
                    });
                    worker.setDaemon(true);
                    worker.start();
                    return future;
                })
                .when(asyncClient)
                .chat(any(com.oracle.bmc.generativeaiinference.requests.ChatRequest.class), isNull());

        var handler = new TestStreamingChatResponseHandler();
        try (var model = OciGenAiCohereStreamingChatModel.builder()
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
            assertThat(handler.completeResponses, contains("HELLO WORLD"));

            verify(asyncClient).chat(any(com.oracle.bmc.generativeaiinference.requests.ChatRequest.class), isNull());
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
                    return ociResponse(STREAMED_DATA);
                })
                .when(syncClient)
                .chat(any(com.oracle.bmc.generativeaiinference.requests.ChatRequest.class));

        var handler = new TestStreamingChatResponseHandler();
        try (var model = OciGenAiCohereStreamingChatModel.builder()
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
            assertThat(handler.completeResponses, contains("HELLO WORLD"));
            verify(syncClient).chat(any(com.oracle.bmc.generativeaiinference.requests.ChatRequest.class));
        }
    }

    @Test
    void doChatUsesConfiguredExecutorServiceForAsyncStreaming() throws Exception {
        var asyncClient = mock(GenerativeAiInferenceAsyncClient.class);
        var executorService = new TrackingExecutorService("oci-cohere-async-executor");
        var requestThread = new AtomicReference<String>();
        var callbackThread = new AtomicReference<String>();
        var completed = new CountDownLatch(1);
        var error = new AtomicReference<Throwable>();

        doAnswer(invocation -> {
                    requestThread.set(Thread.currentThread().getName());
                    return CompletableFuture.completedFuture(ociResponse(STREAMED_DATA));
                })
                .when(asyncClient)
                .chat(any(com.oracle.bmc.generativeaiinference.requests.ChatRequest.class), isNull());

        try {
            try (var model = OciGenAiCohereStreamingChatModel.builder()
                    .modelName("test-model")
                    .compartmentId("test-compartment")
                    .genAiAsyncClient(asyncClient)
                    .executorService(executorService)
                    .build()) {

                model.doChat(chatRequest(), new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                        callbackThread.compareAndSet(
                                null, Thread.currentThread().getName());
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
                });

                assertTrue(completed.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
                assertNull(error.get());
            }

            assertTrue(requestThread.get().startsWith("oci-cohere-async-executor-"));
            assertTrue(callbackThread.get().startsWith("oci-cohere-async-executor-"));
            assertFalse(executorService.shutdownWasRequested());
        } finally {
            executorService.shutdownDelegateNow();
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
                .chat(any(com.oracle.bmc.generativeaiinference.requests.ChatRequest.class), isNull());

        var handler = new TestStreamingChatResponseHandler();
        try (var model = OciGenAiCohereStreamingChatModel.builder()
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
    void doChatShouldEmitCompleteToolCallForToolResponse() throws Exception {
        var handler = new TestStreamingChatResponseHandler();

        try (var model = modelWithAsyncResponse(ociResponse(TOOL_CALL_DATA))) {
            model.doChat(chatRequest(), handler);

            assertTrue(handler.completed.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertNull(handler.error.get());
            assertTrue(handler.partialResponses.isEmpty());
            assertThat(handler.completeResponses, contains((String) null));
            assertThat(
                    handler.completeToolCalls.stream()
                            .map(CompleteToolCall::index)
                            .toList(),
                    contains(0));
            assertThat(
                    handler.completeToolCalls.stream()
                            .map(CompleteToolCall::toolExecutionRequest)
                            .map(ToolExecutionRequest::name)
                            .toList(),
                    contains("getWeather"));
            assertThat(
                    handler.completeToolCalls.stream()
                            .map(CompleteToolCall::toolExecutionRequest)
                            .map(ToolExecutionRequest::arguments)
                            .toList(),
                    contains("{\"city\":\"Munich\"}"));
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
        assertThat(handler.completeResponses, contains("HELLO WORLD"));
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

    private static com.oracle.bmc.generativeaiinference.responses.ChatResponse ociResponse(String streamedData) {
        return com.oracle.bmc.generativeaiinference.responses.ChatResponse.builder()
                .eventStream(new ByteArrayInputStream(streamedData.getBytes(UTF_8)))
                .build();
    }

    private static OciGenAiCohereStreamingChatModel modelWithAsyncResponse(
            com.oracle.bmc.generativeaiinference.responses.ChatResponse response) {
        var asyncClient = mock(GenerativeAiInferenceAsyncClient.class);

        doAnswer(invocation -> {
                    var future = new CompletableFuture<com.oracle.bmc.generativeaiinference.responses.ChatResponse>();
                    var worker = new Thread(() -> future.complete(response));
                    worker.setDaemon(true);
                    worker.start();
                    return future;
                })
                .when(asyncClient)
                .chat(any(com.oracle.bmc.generativeaiinference.requests.ChatRequest.class), isNull());

        return OciGenAiCohereStreamingChatModel.builder()
                .modelName("test-model")
                .compartmentId("test-compartment")
                .genAiAsyncClient(asyncClient)
                .build();
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
                writeLine(output, "data: {\"apiFormat\":\"COHERE\",\"text\":\"HELLO\"}");
                if (!completionAllowed.await(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting to complete stream");
                }
                writeLine(output, "data: {\"apiFormat\":\"COHERE\",\"text\":\" WORLD\"}");
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
        private final List<String> completeResponses = new CopyOnWriteArrayList<>();
        private final List<CompleteToolCall> completeToolCalls = new ArrayList<>();
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
            completeResponses.add(completeResponse.aiMessage().text());
            completed.countDown();
        }

        @Override
        public void onError(Throwable throwable) {
            error.set(throwable);
            completed.countDown();
        }

        @Override
        public void onCompleteToolCall(CompleteToolCall completeToolCall) {
            completeToolCalls.add(completeToolCall);
        }
    }
}
