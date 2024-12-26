package dev.langchain4j.community.xinference.spring;

import static dev.langchain4j.community.xinference.spring.XinferenceUtils.CHAT_MODEL_NAME;
import static dev.langchain4j.community.xinference.spring.XinferenceUtils.EMBEDDING_MODEL_NAME;
import static dev.langchain4j.community.xinference.spring.XinferenceUtils.GENERATE_MODEL_NAME;
import static dev.langchain4j.community.xinference.spring.XinferenceUtils.IMAGE_MODEL_NAME;
import static dev.langchain4j.community.xinference.spring.XinferenceUtils.RERANK_MODEL_NAME;
import static dev.langchain4j.community.xinference.spring.XinferenceUtils.XINFERENCE_IMAGE;
import static dev.langchain4j.community.xinference.spring.XinferenceUtils.launchCmd;
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
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 *
 */
@Testcontainers
class AutoConfigIT {
    ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(AutoConfig.class));

    @Container
    XinferenceContainer chatModelContainer = new XinferenceContainer(XINFERENCE_IMAGE);

    @Test
    void should_provide_chat_model() throws IOException, InterruptedException {
        chatModelContainer.execInContainer("bash", "-c", launchCmd(CHAT_MODEL_NAME));
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.xinference.chat-model.base-url=" + chatModelContainer.getEndpoint(),
                        "langchain4j.community.xinference.chat-model.model-name=" + CHAT_MODEL_NAME,
                        "langchain4j.community.xinference.language-model.logRequests=true",
                        "langchain4j.community.xinference.language-model.logResponses=true")
                .run(context -> {
                    ChatLanguageModel chatLanguageModel = context.getBean(ChatLanguageModel.class);
                    assertThat(chatLanguageModel).isInstanceOf(XinferenceChatModel.class);
                    assertThat(chatLanguageModel.generate("What is the capital of Germany?"))
                            .contains("Berlin");
                    assertThat(context.getBean(XinferenceChatModel.class)).isSameAs(chatLanguageModel);
                });
    }

    @Test
    void should_provide_streaming_chat_model() throws IOException, InterruptedException {
        chatModelContainer.execInContainer("bash", "-c", launchCmd(CHAT_MODEL_NAME));
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.xinference.streaming-chat-model.base-url="
                                + chatModelContainer.getEndpoint(),
                        "langchain4j.community.xinference.streaming-chat-model.model-name=" + CHAT_MODEL_NAME,
                        "langchain4j.community.xinference.language-model.logRequests=true",
                        "langchain4j.community.xinference.language-model.logResponses=true")
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
    void should_provide_language_model() throws IOException, InterruptedException {
        chatModelContainer.execInContainer("bash", "-c", launchCmd(GENERATE_MODEL_NAME));
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.xinference.language-model.base-url=" + chatModelContainer.getEndpoint(),
                        "langchain4j.community.xinference.language-model.model-name=" + GENERATE_MODEL_NAME,
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
    void should_provide_streaming_language_model() throws IOException, InterruptedException {
        chatModelContainer.execInContainer("bash", "-c", launchCmd(GENERATE_MODEL_NAME));
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.xinference.streaming-language-model.base-url="
                                + chatModelContainer.getEndpoint(),
                        "langchain4j.community.xinference.streaming-language-model.model-name=" + GENERATE_MODEL_NAME,
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
    void should_provide_embedding_model() throws IOException, InterruptedException {
        chatModelContainer.execInContainer("bash", "-c", launchCmd(EMBEDDING_MODEL_NAME));
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.xinference.embeddingModel.base-url=" + chatModelContainer.getEndpoint(),
                        "langchain4j.community.xinference.embeddingModel.modelName=" + EMBEDDING_MODEL_NAME,
                        "langchain4j.community.xinference.language-model.logRequests=true",
                        "langchain4j.community.xinference.language-model.logResponses=true")
                .run(context -> {
                    EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);
                    assertThat(embeddingModel).isInstanceOf(XinferenceEmbeddingModel.class);
                    assertThat(embeddingModel.embed("hello world").content().dimension())
                            .isEqualTo(768);
                    assertThat(context.getBean(XinferenceEmbeddingModel.class)).isSameAs(embeddingModel);
                });
    }

    @Test
    void should_provide_sc_model() throws IOException, InterruptedException {
        chatModelContainer.execInContainer("bash", "-c", launchCmd(RERANK_MODEL_NAME));
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.xinference.scoringModel.base-url=" + chatModelContainer.getEndpoint(),
                        "langchain4j.community.xinference.scoringModel.modelName=" + RERANK_MODEL_NAME,
                        "langchain4j.community.xinference.language-model.logRequests=true",
                        "langchain4j.community.xinference.language-model.logResponses=true")
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
                    assertThat(scores.get(0)).isGreaterThan(scores.get(1));
                    assertThat(context.getBean(XinferenceScoringModel.class)).isSameAs(scoringModel);
                });
    }

    @Test
    @Disabled("Not supported to run in a Docker environment without GPU .")
    void should_provide_image_model() throws IOException, InterruptedException {
        chatModelContainer.execInContainer("bash", "-c", launchCmd(IMAGE_MODEL_NAME));
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.xinference.imageModel.base-url=" + chatModelContainer.getEndpoint(),
                        "langchain4j.community.xinference.imageModel.modelName=" + IMAGE_MODEL_NAME,
                        "langchain4j.community.xinference.language-model.logRequests=true",
                        "langchain4j.community.xinference.language-model.logResponses=true")
                .run(context -> {
                    ImageModel imageModel = context.getBean(ImageModel.class);
                    assertThat(imageModel).isInstanceOf(XinferenceImageModel.class);
                    assertThat(imageModel.generate("banana").content().base64Data())
                            .isNotNull();
                    assertThat(context.getBean(XinferenceImageModel.class)).isSameAs(imageModel);
                });
    }
}
