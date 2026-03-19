package dev.langchain4j.community.model.minimax;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static dev.langchain4j.model.output.FinishReason.STOP;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MINIMAX_API_KEY", matches = ".+")
class MiniMaxStreamingChatModelIT {

    private static final String API_KEY = System.getenv("MINIMAX_API_KEY");

    MiniMaxStreamingChatModel streamingModel = MiniMaxStreamingChatModel.builder()
            .apiKey(API_KEY)
            .modelName(MiniMaxChatModelName.MINIMAX_M2_5)
            .logRequests(true)
            .logResponses(true)
            .timeout(Duration.ofSeconds(60))
            .build();

    @Test
    void should_stream_chat_response() throws Exception {
        // given
        UserMessage userMessage = userMessage("What is 1+1? Answer with just the number.");
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();
        StringBuilder contentBuilder = new StringBuilder();

        // when
        streamingModel.chat(List.of(userMessage), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                contentBuilder.append(partialResponse);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        ChatResponse response = future.get(60, TimeUnit.SECONDS);

        // then
        assertThat(response).isNotNull();
        assertThat(response.aiMessage().text()).contains("2");
        assertThat(contentBuilder.toString()).contains("2");
        assertThat(response.metadata().finishReason()).isEqualTo(STOP);
    }

    @Test
    void should_stream_with_system_message() throws Exception {
        // given
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        // when
        streamingModel.chat("What is the capital of France? Answer in one word.", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                // streaming content
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        ChatResponse response = future.get(60, TimeUnit.SECONDS);

        // then
        assertThat(response.aiMessage().text()).containsIgnoringCase("Paris");
    }

    @Test
    void should_stream_with_highspeed_model() throws Exception {
        // given
        MiniMaxStreamingChatModel highspeedModel = MiniMaxStreamingChatModel.builder()
                .apiKey(API_KEY)
                .modelName(MiniMaxChatModelName.MINIMAX_M2_5_HIGHSPEED)
                .timeout(Duration.ofSeconds(60))
                .build();

        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        // when
        highspeedModel.chat("Say hello", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        ChatResponse response = future.get(60, TimeUnit.SECONDS);

        // then
        assertThat(response.aiMessage().text()).isNotBlank();
    }

    @Test
    void should_stream_with_token_usage() throws Exception {
        // given
        CompletableFuture<ChatResponse> future = new CompletableFuture<>();

        // when
        streamingModel.chat("Hello!", new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                future.complete(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        ChatResponse response = future.get(60, TimeUnit.SECONDS);

        // then
        assertThat(response.metadata().tokenUsage()).isNotNull();
        assertThat(response.metadata().tokenUsage().inputTokenCount()).isPositive();
        assertThat(response.metadata().tokenUsage().outputTokenCount()).isPositive();
    }
}
