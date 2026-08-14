package dev.langchain4j.community.zhipu.spring;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.community.model.zhipu.ZhipuAiEmbeddingModel;
import dev.langchain4j.community.model.zhipu.ZhipuAiImageModel;
import dev.langchain4j.community.model.zhipu.ZhipuAiStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.image.ImageModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

@EnabledIfEnvironmentVariable(named = "ZHIPUAI_API_KEY", matches = ".+")
class ZhipuAiAutoConfigurationIT {
    private static final String API_KEY = System.getenv("ZHIPUAI_API_KEY");

    ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ZhipuAiAutoConfiguration.class, TestChatModelListenerAutoConfiguration.class));

    @Test
    void should_provide_chat_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.zhipuai.chat-model.api-key=" + API_KEY,
                        "langchain4j.community.zhipuai.chat-model.model=glm-4-flash",
                        "langchain4j.community.zhipuai.chat-model.max-token=20")
                .run(context -> {
                    ChatModel chatModel = context.getBean(ChatModel.class);
                    assertThat(chatModel).isInstanceOf(ZhipuAiChatModel.class);
                    assertThat(chatModel.chat("1+1=?")).contains("2");
                    assertThat(chatModel.listeners()).isNotEmpty();
                    assertThat(chatModel.listeners().get(0)).isSameAs(context.getBean(ChatModelListener.class));

                    assertThat(context.getBean(ZhipuAiChatModel.class)).isSameAs(chatModel);
                });
    }

    @Test
    void should_provide_streaming_chat_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.zhipuai.streaming-chat-model.api-key=" + API_KEY,
                        "langchain4j.community.zhipuai.streaming-chat-model.model=glm-4-flash",
                        "langchain4j.community.zhipuai.streaming-chat-model.max-token=20")
                .run(context -> {
                    StreamingChatModel streamingChatModel = context.getBean(StreamingChatModel.class);
                    assertThat(streamingChatModel).isInstanceOf(ZhipuAiStreamingChatModel.class);
                    assertThat(streamingChatModel.listeners()).isNotEmpty();
                    assertThat(streamingChatModel.listeners().get(0))
                            .isSameAs(context.getBean(ChatModelListener.class));

                    assertThat(context.getBean(ZhipuAiStreamingChatModel.class)).isSameAs(streamingChatModel);
                });
    }

    @Test
    void should_provide_embedding_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.zhipuai.embedding-model.api-key=" + API_KEY,
                        "langchain4j.community.zhipuai.embedding-model.model=embedding-2",
                        "langchain4j.community.zhipuai.embedding-model.dimensions=256")
                .run(context -> {
                    EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);
                    assertThat(embeddingModel).isInstanceOf(ZhipuAiEmbeddingModel.class);
                    assertThat(embeddingModel.dimension()).isEqualTo(256);
                    assertThat(embeddingModel.embed("hello").content().dimension())
                            .isEqualTo(256);

                    assertThat(context.getBean(ZhipuAiEmbeddingModel.class)).isSameAs(embeddingModel);
                });
    }

    @Test
    void should_provide_image_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.zhipuai.image-model.api-key=" + API_KEY,
                        "langchain4j.community.zhipuai.image-model.model=cogview-3")
                .run(context -> {
                    ImageModel imageModel = context.getBean(ImageModel.class);
                    assertThat(imageModel).isInstanceOf(ZhipuAiImageModel.class);

                    assertThat(context.getBean(ZhipuAiImageModel.class)).isSameAs(imageModel);
                });
    }

    @Test
    void should_provide_chat_model_with_response_format() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.zhipuai.chat-model.api-key=" + API_KEY,
                        "langchain4j.community.zhipuai.chat-model.model=glm-4-flash",
                        "langchain4j.community.zhipuai.chat-model.response-format=JSON")
                .run(context -> {
                    ChatModel chatModel = context.getBean(ChatModel.class);
                    assertThat(chatModel).isInstanceOf(ZhipuAiChatModel.class);
                    assertThat(chatModel.defaultRequestParameters()).isNotNull();

                    assertThat(context.getBean(ZhipuAiChatModel.class)).isSameAs(chatModel);
                });
    }
}
