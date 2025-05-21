package dev.langchain4j.community.dashscope.spring;

import static dev.langchain4j.community.model.dashscope.QwenModelName.TEXT_EMBEDDING_V3;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenChatRequestParameters;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.community.model.dashscope.QwenLanguageModel;
import dev.langchain4j.community.model.dashscope.QwenModelName;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.community.model.dashscope.QwenStreamingLanguageModel;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ToolChoice;
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

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public class AutoConfigIT {
    private static final String API_KEY = System.getenv("DASHSCOPE_API_KEY");
    private static final String CHAT_MODEL = QwenModelName.QWEN_MAX;

    ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(AutoConfig.class));

    @Test
    void should_provide_chat_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.dashscope.chat-model.api-key=" + API_KEY,
                        "langchain4j.community.dashscope.chat-model.model-name=" + CHAT_MODEL,
                        "langchain4j.community.dashscope.chat-model.max-tokens=20")
                .run(context -> {
                    ChatModel chatModel = context.getBean(ChatModel.class);
                    assertThat(chatModel).isInstanceOf(QwenChatModel.class);
                    assertThat(chatModel.chat("What is the capital of Germany?"))
                            .contains("Berlin");

                    assertThat(context.getBean(QwenChatModel.class)).isSameAs(chatModel);
                });
    }

    @Test
    void should_provide_streaming_chat_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.dashscope.streaming-chat-model.api-key=" + API_KEY,
                        "langchain4j.community.dashscope.streaming-chat-model.model-name=" + CHAT_MODEL,
                        "langchain4j.community.dashscope.streaming-chat-model.max-tokens=20")
                .run(context -> {
                    StreamingChatModel streamingChatModel = context.getBean(StreamingChatModel.class);
                    assertThat(streamingChatModel).isInstanceOf(QwenStreamingChatModel.class);
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

                    assertThat(context.getBean(QwenStreamingChatModel.class)).isSameAs(streamingChatModel);
                });
    }

    @Test
    void should_provide_language_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.dashscope.language-model.api-key=" + API_KEY,
                        "langchain4j.community.dashscope.language-model.max-tokens=20")
                .run(context -> {
                    LanguageModel languageModel = context.getBean(LanguageModel.class);
                    assertThat(languageModel).isInstanceOf(QwenLanguageModel.class);
                    assertThat(languageModel
                                    .generate("What is the capital of Germany?")
                                    .content())
                            .contains("Berlin");

                    assertThat(context.getBean(QwenLanguageModel.class)).isSameAs(languageModel);
                });
    }

    @Test
    void should_provide_streaming_language_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.dashscope.streaming-language-model.api-key=" + API_KEY,
                        "langchain4j.community.dashscope.streaming-language-model.max-tokens=20")
                .run(context -> {
                    StreamingLanguageModel streamingLanguageModel = context.getBean(StreamingLanguageModel.class);
                    assertThat(streamingLanguageModel).isInstanceOf(QwenStreamingLanguageModel.class);
                    CompletableFuture<Response<String>> future = new CompletableFuture<>();
                    streamingLanguageModel.generate(
                            "What is the capital of Germany?", new StreamingResponseHandler<>() {

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

                    assertThat(context.getBean(QwenStreamingLanguageModel.class))
                            .isSameAs(streamingLanguageModel);
                });
    }

    @Test
    void should_provide_embedding_model() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.dashscope.embedding-model.api-key=" + API_KEY,
                        "langchain4j.community.dashscope.embedding-model.model-name=" + TEXT_EMBEDDING_V3,
                        "langchain4j.community.dashscope.embedding-model.dimension=512")
                .run(context -> {
                    EmbeddingModel embeddingModel = context.getBean(EmbeddingModel.class);
                    assertThat(embeddingModel).isInstanceOf(QwenEmbeddingModel.class);
                    assertThat(embeddingModel.dimension()).isEqualTo(512);
                    assertThat(embeddingModel.embed("hi").content().dimension()).isEqualTo(512);

                    assertThat(context.getBean(QwenEmbeddingModel.class)).isSameAs(embeddingModel);
                });
    }

    @Test
    void should_provide_chat_model_with_default_parameters() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.dashscope.chat-model.api-key=" + API_KEY,
                        "langchain4j.community.dashscope.chat-model.parameters.enable-search=true",
                        "langchain4j.community.dashscope.chat-model.parameters.top-k=50",
                        "langchain4j.community.dashscope.chat-model.parameters.temperature=0.7",
                        "langchain4j.community.dashscope.chat-model.parameters.frequency-penalty=0.5",
                        "langchain4j.community.dashscope.chat-model.parameters.presence-penalty=0.3",
                        "langchain4j.community.dashscope.chat-model.parameters.max-output-tokens=120",
                        "langchain4j.community.dashscope.chat-model.parameters.model-name=" + CHAT_MODEL,
                        "langchain4j.community.dashscope.chat-model.parameters.tool-choice=AUTO",
                        "langchain4j.community.dashscope.chat-model.parameters.response-format=TEXT",
                        "langchain4j.community.dashscope.chat-model.parameters.seed=42",
                        "langchain4j.community.dashscope.chat-model.parameters.is-multimodal-model=true",
                        "langchain4j.community.dashscope.chat-model.parameters.support-incremental-output=true",
                        "langchain4j.community.dashscope.chat-model.parameters.search-options.enable-source=true",
                        "langchain4j.community.dashscope.chat-model.parameters.search-options.enable-citation=true",
                        "langchain4j.community.dashscope.chat-model.parameters.search-options.citation-format=[<number>]",
                        "langchain4j.community.dashscope.chat-model.parameters.search-options.forced-search=false",
                        "langchain4j.community.dashscope.chat-model.parameters.search-options.search-strategy=standard",
                        "langchain4j.community.dashscope.chat-model.parameters.translation-options.source-lang=English",
                        "langchain4j.community.dashscope.chat-model.parameters.translation-options.target-lang=Chinese",
                        "langchain4j.community.dashscope.chat-model.parameters.translation-options.domains=The sentence is from Ali Cloud IT domain.",
                        "langchain4j.community.dashscope.chat-model.parameters.translation-options.terms[0].source=memory",
                        "langchain4j.community.dashscope.chat-model.parameters.translation-options.terms[0].target=内存",
                        "langchain4j.community.dashscope.chat-model.parameters.translation-options.tm-list[0].source=memory",
                        "langchain4j.community.dashscope.chat-model.parameters.translation-options.tm-list[0].target=内存",
                        "langchain4j.community.dashscope.chat-model.parameters.vl-high-resolution-images=false",
                        "langchain4j.community.dashscope.chat-model.parameters.enable-thinking=true",
                        "langchain4j.community.dashscope.chat-model.parameters.thinking-budget=1000")
                .run(context -> {
                    ChatModel chatModel = context.getBean(ChatModel.class);
                    assertThat(chatModel).isInstanceOf(QwenChatModel.class);
                    assertThat(chatModel.defaultRequestParameters()).isNotNull();
                    assertThat(chatModel.defaultRequestParameters()).isInstanceOf(QwenChatRequestParameters.class);

                    QwenChatRequestParameters defaultParameters =
                            (QwenChatRequestParameters) chatModel.defaultRequestParameters();
                    assertThat(defaultParameters.enableSearch()).isTrue();
                    assertThat(defaultParameters.topK()).isEqualTo(50);
                    assertThat(defaultParameters.temperature()).isEqualTo(0.7);
                    assertThat(defaultParameters.frequencyPenalty()).isEqualTo(0.5);
                    assertThat(defaultParameters.presencePenalty()).isEqualTo(0.3);
                    assertThat(defaultParameters.maxOutputTokens()).isEqualTo(120);
                    assertThat(defaultParameters.toolChoice()).isEqualTo(ToolChoice.AUTO);
                    assertThat(defaultParameters.responseFormat()).isEqualTo(ResponseFormat.TEXT);
                    assertThat(defaultParameters.seed()).isEqualTo(42);
                    assertThat(defaultParameters.isMultimodalModel()).isTrue();
                    assertThat(defaultParameters.supportIncrementalOutput()).isTrue();
                    assertThat(defaultParameters.searchOptions().enableSource()).isTrue();
                    assertThat(defaultParameters.searchOptions().enableCitation())
                            .isTrue();
                    assertThat(defaultParameters.searchOptions().citationFormat())
                            .isEqualTo("[<number>]");
                    assertThat(defaultParameters.searchOptions().forcedSearch()).isFalse();
                    assertThat(defaultParameters.searchOptions().searchStrategy())
                            .isEqualTo("standard");
                    assertThat(defaultParameters.translationOptions().sourceLang())
                            .isEqualTo("English");
                    assertThat(defaultParameters.translationOptions().targetLang())
                            .isEqualTo("Chinese");
                    assertThat(defaultParameters.translationOptions().domains())
                            .isEqualTo("The sentence is from Ali Cloud IT domain.");
                    assertThat(defaultParameters
                                    .translationOptions()
                                    .terms()
                                    .get(0)
                                    .source())
                            .isEqualTo("memory");
                    assertThat(defaultParameters
                                    .translationOptions()
                                    .terms()
                                    .get(0)
                                    .target())
                            .isEqualTo("内存");
                    assertThat(defaultParameters
                                    .translationOptions()
                                    .tmList()
                                    .get(0)
                                    .source())
                            .isEqualTo("memory");
                    assertThat(defaultParameters
                                    .translationOptions()
                                    .tmList()
                                    .get(0)
                                    .target())
                            .isEqualTo("内存");
                    assertThat(defaultParameters.vlHighResolutionImages()).isFalse();
                    assertThat(defaultParameters.enableThinking()).isTrue();
                    assertThat(defaultParameters.thinkingBudget()).isEqualTo(1000);

                    assertThat(context.getBean(QwenChatModel.class)).isSameAs(chatModel);
                });
    }

    @Test
    void should_provide_streaming_chat_model_with_default_parameters() {
        contextRunner
                .withPropertyValues(
                        "langchain4j.community.dashscope.streaming-chat-model.api-key=" + API_KEY,
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.enable-search=true",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.top-k=50",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.temperature=0.7",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.frequency-penalty=0.5",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.presence-penalty=0.3",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.max-output-tokens=120",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.model-name=" + CHAT_MODEL,
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.tool-choice=AUTO",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.response-format=TEXT",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.seed=42",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.is-multimodal-model=true",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.support-incremental-output=true",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.search-options.enable-source=true",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.search-options.enable-citation=true",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.search-options.citation-format=[<number>]",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.search-options.forced-search=false",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.search-options.search-strategy=standard",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.translation-options.source-lang=English",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.translation-options.target-lang=Chinese",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.translation-options.domains=The sentence is from Ali Cloud IT domain.",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.translation-options.terms[0].source=memory",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.translation-options.terms[0].target=内存",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.translation-options.tm-list[0].source=memory",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.translation-options.tm-list[0].target=内存",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.vl-high-resolution-images=false",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.enable-thinking=true",
                        "langchain4j.community.dashscope.streaming-chat-model.parameters.thinking-budget=1000")
                .run(context -> {
                    StreamingChatModel streamingChatModel = context.getBean(StreamingChatModel.class);
                    assertThat(streamingChatModel).isInstanceOf(QwenStreamingChatModel.class);
                    assertThat(streamingChatModel.defaultRequestParameters()).isNotNull();
                    assertThat(streamingChatModel.defaultRequestParameters())
                            .isInstanceOf(QwenChatRequestParameters.class);

                    QwenChatRequestParameters defaultParameters =
                            (QwenChatRequestParameters) streamingChatModel.defaultRequestParameters();
                    assertThat(defaultParameters.enableSearch()).isTrue();
                    assertThat(defaultParameters.topK()).isEqualTo(50);
                    assertThat(defaultParameters.temperature()).isEqualTo(0.7);
                    assertThat(defaultParameters.frequencyPenalty()).isEqualTo(0.5);
                    assertThat(defaultParameters.presencePenalty()).isEqualTo(0.3);
                    assertThat(defaultParameters.maxOutputTokens()).isEqualTo(120);
                    assertThat(defaultParameters.toolChoice()).isEqualTo(ToolChoice.AUTO);
                    assertThat(defaultParameters.responseFormat()).isEqualTo(ResponseFormat.TEXT);
                    assertThat(defaultParameters.seed()).isEqualTo(42);
                    assertThat(defaultParameters.isMultimodalModel()).isTrue();
                    assertThat(defaultParameters.supportIncrementalOutput()).isTrue();
                    assertThat(defaultParameters.searchOptions().enableSource()).isTrue();
                    assertThat(defaultParameters.searchOptions().enableCitation())
                            .isTrue();
                    assertThat(defaultParameters.searchOptions().citationFormat())
                            .isEqualTo("[<number>]");
                    assertThat(defaultParameters.searchOptions().forcedSearch()).isFalse();
                    assertThat(defaultParameters.searchOptions().searchStrategy())
                            .isEqualTo("standard");
                    assertThat(defaultParameters.translationOptions().sourceLang())
                            .isEqualTo("English");
                    assertThat(defaultParameters.translationOptions().targetLang())
                            .isEqualTo("Chinese");
                    assertThat(defaultParameters.translationOptions().domains())
                            .isEqualTo("The sentence is from Ali Cloud IT domain.");
                    assertThat(defaultParameters
                                    .translationOptions()
                                    .terms()
                                    .get(0)
                                    .source())
                            .isEqualTo("memory");
                    assertThat(defaultParameters
                                    .translationOptions()
                                    .terms()
                                    .get(0)
                                    .target())
                            .isEqualTo("内存");
                    assertThat(defaultParameters
                                    .translationOptions()
                                    .tmList()
                                    .get(0)
                                    .source())
                            .isEqualTo("memory");
                    assertThat(defaultParameters
                                    .translationOptions()
                                    .tmList()
                                    .get(0)
                                    .target())
                            .isEqualTo("内存");
                    assertThat(defaultParameters.vlHighResolutionImages()).isFalse();
                    assertThat(defaultParameters.enableThinking()).isTrue();
                    assertThat(defaultParameters.thinkingBudget()).isEqualTo(1000);

                    assertThat(context.getBean(QwenStreamingChatModel.class)).isSameAs(streamingChatModel);
                });
    }
}
