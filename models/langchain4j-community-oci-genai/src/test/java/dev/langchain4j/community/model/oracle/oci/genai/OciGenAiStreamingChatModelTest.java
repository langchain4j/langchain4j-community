package dev.langchain4j.community.model.oracle.oci.genai;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OciGenAiStreamingChatModelTest {

    private static final String STREAMED_DATA =
            """
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
                    var request = invocation.getArgument(
                            0, com.oracle.bmc.generativeaiinference.requests.ChatRequest.class);
                    @SuppressWarnings("unchecked")
                    AsyncHandler<
                                    com.oracle.bmc.generativeaiinference.requests.ChatRequest,
                                    com.oracle.bmc.generativeaiinference.responses.ChatResponse>
                            asyncHandler = invocation.getArgument(1, AsyncHandler.class);

                    var future = new CompletableFuture<com.oracle.bmc.generativeaiinference.responses.ChatResponse>();
                    var worker = new Thread(() -> {
                        requestScheduled.countDown();
                        try {
                            assertTrue(releaseResponse.await(2, TimeUnit.SECONDS));
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

            assertTrue(requestScheduled.await(2, TimeUnit.SECONDS));
            assertTrue(handler.partialResponses.isEmpty());

            releaseResponse.countDown();

            assertTrue(handler.completed.await(2, TimeUnit.SECONDS));
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
                    assertTrue(releaseResponse.await(2, TimeUnit.SECONDS));
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

            assertTrue(requestStarted.await(2, TimeUnit.SECONDS));
            assertTrue(handler.partialResponses.isEmpty());

            releaseResponse.countDown();

            assertTrue(handler.completed.await(2, TimeUnit.SECONDS));
            assertNull(handler.error.get());
            assertThat(handler.partialResponses, contains("HELLO", " WORLD"));

            verify(syncClient).chat(any(com.oracle.bmc.generativeaiinference.requests.ChatRequest.class));
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

    private static class TestStreamingChatResponseHandler implements StreamingChatResponseHandler {

        private final List<String> partialResponses = new CopyOnWriteArrayList<>();
        private final CountDownLatch completed = new CountDownLatch(1);
        private final AtomicReference<Throwable> error = new AtomicReference<>();

        @Override
        public void onPartialResponse(String partialResponse) {
            partialResponses.add(partialResponse);
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
