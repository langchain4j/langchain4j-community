package dev.langchain4j.community.novitaai.spring;

import static dev.langchain4j.data.message.UserMessage.userMessage;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.model.novitaai.NovitaAiChatModel;
import dev.langchain4j.community.model.novitaai.NovitaAiStreamingChatModel;
import dev.langchain4j.community.novitaai.spring.AutoConfig;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@EnabledIfEnvironmentVariable(named = "NOVITA_AI_API_KEY", matches = ".+")
class AutoConfigIT {

    private static final String API_KEY = System.getenv("NOVITA_AI_API_KEY");

    ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(AutoConfig.class));

    @Test
    void should_provide_chat_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.novitaai.chat-model.api-key=" + API_KEY,
                        "langchain4j.community.novitaai.chat-model.model-name=deepseek/deepseek_v3")
                .run(context -> {
                    ChatLanguageModel chatLanguageModel = context.getBean(ChatLanguageModel.class);
                    assertThat(chatLanguageModel).isInstanceOf(NovitaAiChatModel.class);
                    assertThat(chatLanguageModel.chat("What is the capital of Germany?"))
                            .containsIgnoringCase("Berlin");

                    assertThat(context.getBean(NovitaAiChatModel.class)).isSameAs(chatLanguageModel);
                });
    }

    @Test
    void should_provide_streaming_chat_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.novitaai.streamingChatModel.api-key=" + API_KEY,
                        "langchain4j.community.novitaai.streamingChatModel.model-name=deepseek/deepseek_v3")
                .run(context -> {
                    StreamingChatLanguageModel streamingChatLanguageModel =
                            context.getBean(StreamingChatLanguageModel.class);
                    assertThat(streamingChatLanguageModel).isInstanceOf(NovitaAiStreamingChatModel.class);
                    CompletableFuture<ChatResponse> future = new CompletableFuture<>();
                    streamingChatLanguageModel.chat("What is the capital of China?", new StreamingChatResponseHandler() {

                        @Override
                        public void onPartialResponse(String token) {}

                        @Override
                        public void onCompleteResponse(ChatResponse response) {
                            future.complete(response);
                        }

                        @Override
                        public void onError(Throwable error) {}
                    });
                    ChatResponse response = future.get(60, SECONDS);
                    assertThat(response.aiMessage().text()).isNotNull();
                    assertThat(response.aiMessage().text()).containsIgnoringCase("Beijing");
                    assertThat(context.getBean(NovitaAiStreamingChatModel.class)).isSameAs(streamingChatLanguageModel);
                });
    }
}
