package dev.langchain4j.community.cohere.spring;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.cohere.CohereAutoConfiguration;
import dev.langchain4j.community.model.CohereChatModel;
import dev.langchain4j.community.model.CohereStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@EnabledIfEnvironmentVariable(named = "CO_API_KEY", matches = ".+")
class CohereAutoConfigurationIT {

    private static final String API_KEY = System.getenv("CO_API_KEY");
    private static final String MODEL_NAME = "command-r7b-12-2024";

    ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(
                    AutoConfigurations.of(CohereAutoConfiguration.class, TestChatModelListenerAutoConfiguration.class));

    @Test
    void should_provide_chat_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.cohere.chat-model.api-key=" + API_KEY,
                        "langchain4j.community.cohere.chat-model.model-name=" + MODEL_NAME,
                        "langchain4j.community.cohere.chat-model.max-tokens=20",
                        "langchain4j.community.cohere.chat-model.log-requests=true",
                        "langchain4j.community.cohere.chat-model.log-responses=true")
                .run(context -> {
                    ChatModel chatModel = context.getBean(ChatModel.class);
                    assertThat(chatModel).isInstanceOf(CohereChatModel.class);

                    assertThat(chatModel.chat("What is the capital of Venezuela"))
                            .containsIgnoringCase("Caracas");

                    assertThat(chatModel.listeners()).isNotEmpty();
                    assertThat(chatModel.listeners().get(0)).isSameAs(context.getBean(ChatModelListener.class));

                    assertThat(context.getBean(CohereChatModel.class)).isSameAs(chatModel);
                });
    }

    @Test
    void should_provide_streaming_chat_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.cohere.streaming-chat-model.api-key=" + API_KEY,
                        "langchain4j.community.cohere.streaming-chat-model.model-name=" + MODEL_NAME,
                        "langchain4j.community.cohere.streaming-chat-model.max-tokens=20",
                        "langchain4j.community.cohere.streaming-chat-model.log-requests=true",
                        "langchain4j.community.cohere.streaming-chat-model.log-responses=true")
                .run(context -> {
                    StreamingChatModel streamingChatModel = context.getBean(StreamingChatModel.class);
                    assertThat(streamingChatModel).isInstanceOf(CohereStreamingChatModel.class);
                    assertThat(streamingChatModel.listeners()).isNotEmpty();
                    assertThat(streamingChatModel.listeners().get(0))
                            .isSameAs(context.getBean(ChatModelListener.class));

                    CompletableFuture<ChatResponse> future = new CompletableFuture<>();
                    streamingChatModel.chat("What is the capital of Spain?", new StreamingChatResponseHandler() {
                        @Override
                        public void onCompleteResponse(final ChatResponse response) {
                            future.complete(response);
                        }

                        @Override
                        public void onError(final Throwable throwable) {}
                    });

                    ChatResponse response = future.get(60, SECONDS);
                    assertThat(response.aiMessage().text()).containsIgnoringCase("Madrid");

                    assertThat(context.getBean(CohereStreamingChatModel.class)).isSameAs(streamingChatModel);
                });
    }
}
