package dev.langchain4j.community.xinference.spring;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.model.xinference.XinferenceChatModel;
import dev.langchain4j.community.model.xinference.XinferenceEmbeddingModel;
import dev.langchain4j.community.model.xinference.XinferenceImageModel;
import dev.langchain4j.community.model.xinference.XinferenceLanguageModel;
import dev.langchain4j.community.model.xinference.XinferenceScoringModel;
import dev.langchain4j.community.model.xinference.XinferenceStreamingChatModel;
import dev.langchain4j.community.model.xinference.XinferenceStreamingLanguageModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.scoring.ScoringModel;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Xinference Cloud
 * https://docs.inference.top/zh
 */
@EnabledIfEnvironmentVariable(named = "XINFERENCE_API_KEY", matches = ".+")
class AutoConfigIT {
    private static final String API_KEY = System.getenv("XINFERENCE_API_KEY");
    private static final String BASE_URL = System.getenv("XINFERENCE_BASE_URL");
    ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(AutoConfig.class));

    @AfterEach
    void waitForNextTest() throws InterruptedException {
        Thread.sleep(3000); // 每个测试后延时3秒
    }

    @Test
    void should_provide_chat_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.xinference.chat-model.base-url=" + BASE_URL,
                        "langchain4j.community.xinference.chat-model.api-key=" + API_KEY,
                        "langchain4j.community.xinference.chat-model.model-name=qwen2-vl-instruct")
                .run(context -> {
                    ChatLanguageModel chatLanguageModel = context.getBean(ChatLanguageModel.class);
                    assertThat(chatLanguageModel).isInstanceOf(XinferenceChatModel.class);
                    assertThat(chatLanguageModel.generate("What is the capital of Germany?"))
                            .contains("Berlin");
                    assertThat(context.getBean(XinferenceChatModel.class)).isSameAs(chatLanguageModel);
                });
    }

    @Test
    void should_provide_streaming_chat_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.xinference.streaming-chat-model.base-url=" + BASE_URL,
                        "langchain4j.community.xinference.streaming-chat-model.api-key=" + API_KEY,
                        "langchain4j.community.xinference.streaming-chat-model.model-name=qwen2-vl-instruct")
                .run(context -> {
                    StreamingChatLanguageModel streamingChatLanguageModel =
                            context.getBean(StreamingChatLanguageModel.class);
                    assertThat(streamingChatLanguageModel).isInstanceOf(XinferenceStreamingChatModel.class);
                    CompletableFuture<Response<AiMessage>> future = new CompletableFuture<>();
                    streamingChatLanguageModel.generate(
                            "What is the capital of Germany?", new StreamingResponseHandler<AiMessage>() {
                                @Override
                                public void onNext(String token) {}

                                @Override
                                public void onComplete(Response<AiMessage> response) {
                                    future.complete(response);
                                }

                                @Override
                                public void onError(Throwable error) {}
                            });
                    Response<AiMessage> response = future.get(60, SECONDS);
                    assertThat(response.content().text()).contains("Berlin");
                    assertThat(context.getBean(XinferenceStreamingChatModel.class))
                            .isSameAs(streamingChatLanguageModel);
                });
    }

    @Test
    void should_provide_language_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.xinference.language-model.base-url=" + BASE_URL,
                        "langchain4j.community.xinference.language-model.api-key=" + API_KEY,
                        "langchain4j.community.xinference.language-model.model-name=qwen2-vl-instruct",
                        "langchain4j.community.xinference.language-model.logRequests=true",
                        "langchain4j.community.xinference.language-model.logResponses=true")
                .run(context -> {
                    LanguageModel languageModel = context.getBean(LanguageModel.class);
                    assertThat(languageModel).isInstanceOf(XinferenceLanguageModel.class);
                    assertThat(languageModel
                                    .generate("What is the capital of Germany?")
                                    .content())
                            .contains("Berlin");
                    assertThat(context.getBean(XinferenceLanguageModel.class)).isSameAs(languageModel);
                });
    }

    @Test
    void should_provide_streaming_language_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.xinference.streaming-language-model.base-url=" + BASE_URL,
                        "langchain4j.community.xinference.streaming-language-model.api-key=" + API_KEY,
                        "langchain4j.community.xinference.streaming-language-model.model-name=qwen2-vl-instruct",
                        "langchain4j.community.xinference.streaming-language-model.logRequests=true",
                        "langchain4j.community.xinference.streaming-language-model.logResponses=true")
                .run(context -> {
                    StreamingLanguageModel streamingLanguageModel = context.getBean(StreamingLanguageModel.class);
                    assertThat(streamingLanguageModel).isInstanceOf(XinferenceStreamingLanguageModel.class);
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
                    assertThat(response.content()).contains("Berlin");

                    assertThat(context.getBean(XinferenceStreamingLanguageModel.class))
                            .isSameAs(streamingLanguageModel);
                });
    }

    @Test
    void should_provide_embedding_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.xinference.embeddingModel.base-url=" + BASE_URL,
                        "langchain4j.community.xinference.embeddingModel.api-key=" + API_KEY,
                        "langchain4j.community.xinference.embeddingModel.modelName=bge-m3")
                .run(context -> {
                    EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);
                    assertThat(embeddingModel).isInstanceOf(XinferenceEmbeddingModel.class);
                    assertThat(embeddingModel.embed("hello world").content().dimension())
                            .isEqualTo(1024);
                    assertThat(context.getBean(XinferenceEmbeddingModel.class)).isSameAs(embeddingModel);
                });
    }

    @Test
    @Disabled("Xinference Cloud Not Support")
    void should_provide_sc_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.xinference.scoringModel.base-url=" + BASE_URL,
                        "langchain4j.community.xinference.scoringModel.api-key=" + API_KEY,
                        "langchain4j.community.xinference.scoringModel.modelName=bge-m3")
                .run(context -> {
                    ScoringModel scoringModel = context.getBean(ScoringModel.class);
                    assertThat(scoringModel).isInstanceOf(XinferenceScoringModel.class);
                    TextSegment catSegment = TextSegment.from("The Maine Coon is a large domesticated cat breed.");
                    TextSegment dogSegment = TextSegment.from(
                            "The sweet-faced, lovable Labrador Retriever is one of America's most popular dog breeds, year after year.");
                    List<TextSegment> segments = Arrays.asList(catSegment, dogSegment);
                    String query = "tell me about dogs";
                    Response<List<Double>> response = scoringModel.scoreAll(segments, query);
                    List<Double> scores = response.content();
                    assertThat(scores).hasSize(2);
                    assertThat(scores.get(0)).isLessThan(scores.get(1));
                    assertThat(context.getBean(XinferenceScoringModel.class)).isSameAs(scoringModel);
                });
    }

    @Test
    void should_provide_image_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.xinference.imageModel.base-url=" + BASE_URL,
                        "langchain4j.community.xinference.imageModel.api-key=" + API_KEY,
                        "langchain4j.community.xinference.imageModel.modelName=sd3-medium")
                .run(context -> {
                    ImageModel imageModel = context.getBean(ImageModel.class);
                    assertThat(imageModel).isInstanceOf(XinferenceImageModel.class);
                    assertThat(imageModel.generate("banana").content().base64Data())
                            .isNotNull();
                    assertThat(context.getBean(XinferenceImageModel.class)).isSameAs(imageModel);
                });
    }
}
