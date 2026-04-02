package dev.langchain4j.community.model.oracle.oci.genai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.oracle.bmc.auth.BasicAuthenticationDetailsProvider;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceAsyncClient;
import com.oracle.bmc.generativeaiinference.GenerativeAiInferenceClient;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BaseChatModelTest {

    @Test
    void setNotNull() {
        assertNull(setIfNotNull(null));
        assertNull(setIfNotNull(Map.of()));
        assertNull(setIfNotNull(List.of()));
        assertNull(setIfNotNull(Set.of()));
        assertNull(setIfNotNull(new HashMap<>(0)));

        var map = new HashMap<>();
        map.put("key", "value");
        assertEquals(map, setIfNotNull(map));
        assertEquals(5, setIfNotNull(5));
        assertEquals("5", setIfNotNull("5"));
        assertEquals(List.of("5"), setIfNotNull(List.of("5")));
    }

    @Test
    void genericSyncModelRequiresSyncClientConfiguration() {
        var asyncClient = mock(GenerativeAiInferenceAsyncClient.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> OciGenAiChatModel.builder()
                        .modelName("test-model")
                        .compartmentId("test-compartment")
                        .genAiAsyncClient(asyncClient)
                        .build());
    }

    @Test
    void cohereSyncModelRequiresSyncClientConfiguration() {
        var asyncClient = mock(GenerativeAiInferenceAsyncClient.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> OciGenAiCohereChatModel.builder()
                        .modelName("test-model")
                        .compartmentId("test-compartment")
                        .genAiAsyncClient(asyncClient)
                        .build());
    }

    @Test
    void manualSyncClientDisablesImplicitAsyncClientCreation() {
        var syncClient = mock(GenerativeAiInferenceClient.class);
        var authProvider = testAuthProvider();

        var builder = OciGenAiStreamingChatModel.builder()
                .modelName("test-model")
                .compartmentId("test-compartment")
                .genAiClient(syncClient)
                .authProvider(authProvider);

        assertTrue(builder.hasGenAiClient());
        assertFalse(builder.hasGenAiAsyncClient());
    }

    @Test
    void authProviderStillEnablesImplicitAsyncClientCreationWithoutManualSyncClient() {
        var authProvider = testAuthProvider();

        var builder = OciGenAiStreamingChatModel.builder()
                .modelName("test-model")
                .compartmentId("test-compartment")
                .authProvider(authProvider);

        assertTrue(builder.hasGenAiAsyncClient());
    }

    @Test
    void genericSyncBuilderShouldKeepAsyncClientSetterPublicForCompatibility() throws NoSuchMethodException {
        var method =
                OciGenAiChatModel.Builder.class.getMethod("genAiAsyncClient", GenerativeAiInferenceAsyncClient.class);

        assertTrue(Modifier.isPublic(method.getModifiers()));
    }

    @Test
    void cohereSyncBuilderShouldKeepAsyncClientSetterPublicForCompatibility() throws NoSuchMethodException {
        var method = OciGenAiCohereChatModel.Builder.class.getMethod(
                "genAiAsyncClient", GenerativeAiInferenceAsyncClient.class);

        assertTrue(Modifier.isPublic(method.getModifiers()));
    }

    @Test
    void syncModelShouldNotCreateLazyClientAfterClose() {
        try (var model = OciGenAiChatModel.builder()
                .modelName("test-model")
                .compartmentId("test-compartment")
                .authProvider(testAuthProvider())
                .build()) {

            model.close();

            var error = assertThrows(
                    IllegalStateException.class,
                    () -> model.doChat(ChatRequest.builder()
                            .messages(UserMessage.from("Hello"))
                            .build()));

            assertEquals("OCI GenAI model is closed.", error.getMessage());
        }
    }

    @Test
    void streamingModelShouldNotCreateLazyAsyncClientAfterClose() throws InterruptedException {
        try (var model = OciGenAiStreamingChatModel.builder()
                .modelName("test-model")
                .compartmentId("test-compartment")
                .authProvider(testAuthProvider())
                .build()) {

            model.close();

            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<Throwable> error = new AtomicReference<>();

            model.doChat(
                    ChatRequest.builder().messages(UserMessage.from("Hello")).build(),
                    new StreamingChatResponseHandler() {
                        @Override
                        public void onPartialResponse(String partialResponse) {}

                        @Override
                        public void onCompleteResponse(ChatResponse completeResponse) {
                            done.countDown();
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            error.set(throwable);
                            done.countDown();
                        }
                    });

            assertTrue(done.await(2, TimeUnit.SECONDS));
            assertTrue(error.get() instanceof IllegalStateException);
            assertEquals("OCI GenAI model is closed.", error.get().getMessage());
        }
    }

    Object setIfNotNull(Object value) {
        AtomicReference<Object> ref = new AtomicReference<>();
        BaseChatModel.setIfNotNull(value, ref::set);
        return ref.get();
    }

    private static BasicAuthenticationDetailsProvider testAuthProvider() {
        return new BasicAuthenticationDetailsProvider() {
            @Override
            public String getKeyId() {
                return "test-key";
            }

            @Override
            public ByteArrayInputStream getPrivateKey() {
                return new ByteArrayInputStream(new byte[0]);
            }

            @Override
            public String getPassPhrase() {
                return null;
            }

            @Override
            public char[] getPassphraseCharacters() {
                return null;
            }
        };
    }
}
