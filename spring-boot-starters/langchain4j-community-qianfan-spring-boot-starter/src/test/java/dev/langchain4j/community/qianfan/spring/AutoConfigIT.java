package dev.langchain4j.community.qianfan.spring;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.model.qianfan.QianfanChatModel;
import dev.langchain4j.community.model.qianfan.QianfanEmbeddingModel;
import dev.langchain4j.community.model.qianfan.QianfanLanguageModel;
import dev.langchain4j.community.model.qianfan.QianfanStreamingChatModel;
import dev.langchain4j.community.model.qianfan.QianfanStreamingLanguageModel;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * @Author: fanjia
 * @createTime: 2024年04月18日 12:54:25
 * @version: 1.0
 * @Description:
 */
@EnabledIfEnvironmentVariable(named = "QIANFAN_API_KEY", matches = ".+")
class AutoConfigIT {

    private static final String API_KEY = System.getenv("QIANFAN_API_KEY");
    private static final String SECRET_KEY = System.getenv("QIANFAN_SECRET_KEY");

    ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(AutoConfig.class));

    @Test
    void should_provide_chat_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.qianfan.chat-model.api-key=" + API_KEY,
                        "langchain4j.community.qianfan.chat-model.secret-key=" + SECRET_KEY,
                        "langchain4j.community.qianfan.chat-model.model-name=ERNIE-Bot")
                .run(context -> {
                    ChatModel chatModel = context.getBean(ChatModel.class);
                    assertThat(chatModel).isInstanceOf(QianfanChatModel.class);
                    assertThat(chatModel.chat("What is the capital of Germany?"))
                            .contains("Berlin");

                    assertThat(context.getBean(QianfanChatModel.class)).isSameAs(chatModel);
                });
    }

    @Test
    void should_provide_streaming_chat_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.qianfan.streamingChatModel.api-key=" + API_KEY,
                        "langchain4j.community.qianfan.streamingChatModel.secret-key=" + SECRET_KEY,
                        "langchain4j.community.qianfan.streamingChatModel.model-name=ERNIE-Bot")
                .run(context -> {
                    StreamingChatModel streamingChatModel = context.getBean(StreamingChatModel.class);
                    assertThat(streamingChatModel).isInstanceOf(QianfanStreamingChatModel.class);
                    CompletableFuture<ChatResponse> future = new CompletableFuture<>();
                    streamingChatModel.chat("德国的首都是哪里?", new StreamingChatResponseHandler() {

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

                    assertThat(context.getBean(QianfanStreamingChatModel.class)).isSameAs(streamingChatModel);
                });
    }

    @Test
    void should_provide_language_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.qianfan.languageModel.api-key=" + API_KEY,
                        "langchain4j.community.qianfan.languageModel.secret-key=" + SECRET_KEY,
                        "langchain4j.community.qianfan.languageModel.modelName=CodeLlama-7b-Instruct",
                        "langchain4j.community.qianfan.languageModel.logRequests=true",
                        "langchain4j.community.qianfan.languageModel.logResponses=true")
                .run(context -> {
                    LanguageModel languageModel = context.getBean(LanguageModel.class);
                    assertThat(languageModel).isInstanceOf(QianfanLanguageModel.class);
                    assertThat(languageModel
                                    .generate("What is the capital of Germany?")
                                    .content())
                            .contains("Berlin");
                    assertThat(context.getBean(QianfanLanguageModel.class)).isSameAs(languageModel);
                });
    }

    @Test
    void should_provide_streaming_language_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.qianfan.streamingLanguageModel.api-key=" + API_KEY,
                        "langchain4j.community.qianfan.streamingLanguageModel.secret-key=" + SECRET_KEY,
                        "langchain4j.community.qianfan.streamingLanguageModel.modelName=CodeLlama-7b-Instruct",
                        "langchain4j.community.qianfan.streamingLanguageModel.logRequests=true",
                        "langchain4j.community.qianfan.streamingLanguageModel.logResponses=true")
                .run(context -> {
                    StreamingLanguageModel streamingLanguageModel = context.getBean(StreamingLanguageModel.class);
                    assertThat(streamingLanguageModel).isInstanceOf(QianfanStreamingLanguageModel.class);
                    CompletableFuture<Response<String>> future = new CompletableFuture<>();
                    streamingLanguageModel.generate(
                            "What is the capital of Germany?", new StreamingResponseHandler<String>() {

                                @Override
                                public void onNext(String token) {}

                                @Override
                                public void onComplete(Response<String> response) {
                                    future.complete(response);
                                }

                                @Override
                                public void onError(Throwable error) {}
                            });
                    Response<String> response = future.get(60, SECONDS);
                    assertThat(response.content()).isNotNull();

                    assertThat(context.getBean(QianfanStreamingLanguageModel.class))
                            .isSameAs(streamingLanguageModel);
                });
    }

    @Test
    void should_provide_embedding_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.qianfan.embeddingModel.api-key=" + API_KEY,
                        "langchain4j.community.qianfan.embeddingModel.secret-key=" + SECRET_KEY,
                        "langchain4j.community.qianfan.embeddingModel.modelName=bge-large-zh")
                .run(context -> {
                    EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);
                    assertThat(embeddingModel).isInstanceOf(QianfanEmbeddingModel.class);
                    assertThat(embeddingModel.embed("hi").content().dimension()).isEqualTo(1024);

                    assertThat(context.getBean(QianfanEmbeddingModel.class)).isSameAs(embeddingModel);
                });
    }
}
