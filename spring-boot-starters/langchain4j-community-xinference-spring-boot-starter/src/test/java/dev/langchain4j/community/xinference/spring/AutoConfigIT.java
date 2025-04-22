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
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
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
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(XinferenceAutoConfiguration.class));

    @Container
    XinferenceContainer chatModelContainer = new XinferenceContainer(XINFERENCE_IMAGE);

    @Test
    void should_provide_chat_model() throws IOException, InterruptedException {
        chatModelContainer.execInContainer("bash", "-c", launchCmd(CHAT_MODEL_NAME));
        contextRunner
                .withPropertyValues(
                        ChatModelProperties.PREFIX + ".base-url=" + chatModelContainer.getEndpoint(),
                        ChatModelProperties.PREFIX + ".model-name=" + CHAT_MODEL_NAME,
                        ChatModelProperties.PREFIX + ".logRequests=true",
                        ChatModelProperties.PREFIX + ".logResponses=true")
                .run(context -> {
                    ChatModel chatModel = context.getBean(ChatModel.class);
                    assertThat(chatModel).isInstanceOf(XinferenceChatModel.class);
                    assertThat(chatModel.chat("What is the capital of Germany?"))
                            .contains("Berlin");
                    assertThat(context.getBean(XinferenceChatModel.class)).isSameAs(chatModel);
                });
    }

    @Test
    void should_provide_streaming_chat_model() throws IOException, InterruptedException {
        chatModelContainer.execInContainer("bash", "-c", launchCmd(CHAT_MODEL_NAME));
        contextRunner
                .withPropertyValues(
                        StreamingChatModelProperties.PREFIX + ".base-url=" + chatModelContainer.getEndpoint(),
                        StreamingChatModelProperties.PREFIX + ".model-name=" + CHAT_MODEL_NAME,
                        StreamingChatModelProperties.PREFIX + ".logRequests=true",
                        StreamingChatModelProperties.PREFIX + ".logResponses=true")
                .run(context -> {
                    StreamingChatModel streamingChatModel = context.getBean(StreamingChatModel.class);
                    assertThat(streamingChatModel).isInstanceOf(XinferenceStreamingChatModel.class);
                    CompletableFuture<ChatResponse> future = new CompletableFuture<>();
                    streamingChatModel.chat("What is the capital of Germany?", new StreamingChatResponseHandler() {
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
                    assertThat(response.aiMessage().text()).contains("Berlin");
                    assertThat(context.getBean(XinferenceStreamingChatModel.class))
                            .isSameAs(streamingChatModel);
                });
    }

    @Test
    void should_provide_language_model() throws IOException, InterruptedException {
        chatModelContainer.execInContainer("bash", "-c", launchCmd(GENERATE_MODEL_NAME));
        contextRunner
                .withPropertyValues(
                        LanguageModelProperties.PREFIX + ".base-url=" + chatModelContainer.getEndpoint(),
                        LanguageModelProperties.PREFIX + ".model-name=" + GENERATE_MODEL_NAME,
                        LanguageModelProperties.PREFIX + ".logRequests=true",
                        LanguageModelProperties.PREFIX + ".logResponses=true")
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
                        StreamingLanguageModelProperties.PREFIX + ".base-url=" + chatModelContainer.getEndpoint(),
                        StreamingLanguageModelProperties.PREFIX + ".model-name=" + GENERATE_MODEL_NAME,
                        StreamingLanguageModelProperties.PREFIX + ".logRequests=true",
                        StreamingLanguageModelProperties.PREFIX + ".logResponses=true")
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
                        EmbeddingModelProperties.PREFIX + ".base-url=" + chatModelContainer.getEndpoint(),
                        EmbeddingModelProperties.PREFIX + ".modelName=" + EMBEDDING_MODEL_NAME,
                        EmbeddingModelProperties.PREFIX + ".logRequests=true",
                        EmbeddingModelProperties.PREFIX + ".logResponses=true")
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
                        ScoringModelProperties.PREFIX + ".base-url=" + chatModelContainer.getEndpoint(),
                        ScoringModelProperties.PREFIX + ".modelName=" + RERANK_MODEL_NAME,
                        ScoringModelProperties.PREFIX + ".logRequests=true",
                        ScoringModelProperties.PREFIX + ".logResponses=true")
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
                        ImageModelProperties.PREFIX + ".base-url=" + chatModelContainer.getEndpoint(),
                        ImageModelProperties.PREFIX + ".modelName=" + IMAGE_MODEL_NAME,
                        ImageModelProperties.PREFIX + ".logRequests=true",
                        ImageModelProperties.PREFIX + ".logResponses=true")
                .run(context -> {
                    ImageModel imageModel = context.getBean(ImageModel.class);
                    assertThat(imageModel).isInstanceOf(XinferenceImageModel.class);
                    assertThat(imageModel.generate("banana").content().base64Data())
                            .isNotNull();
                    assertThat(context.getBean(XinferenceImageModel.class)).isSameAs(imageModel);
                });
    }
}
