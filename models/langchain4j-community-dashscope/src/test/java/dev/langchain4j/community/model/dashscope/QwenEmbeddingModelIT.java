package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenEmbeddingModel.TYPE_KEY;
import static dev.langchain4j.community.model.dashscope.QwenEmbeddingModel.TYPE_QUERY;
import static dev.langchain4j.community.model.dashscope.QwenModelName.TEXT_EMBEDDING_V1;
import static dev.langchain4j.community.model.dashscope.QwenModelName.TEXT_EMBEDDING_V2;
import static dev.langchain4j.community.model.dashscope.QwenModelName.TEXT_EMBEDDING_V3;
import static dev.langchain4j.community.model.dashscope.QwenTestHelper.apiKey;
import static dev.langchain4j.data.segment.TextSegment.textSegment;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
class QwenEmbeddingModelIT {

    private QwenEmbeddingModel getModel(String modelName) {
        return getModel(modelName, null);
    }

    private QwenEmbeddingModel getModel(String modelName, Integer dimension) {
        return QwenEmbeddingModel.builder()
                .apiKey(apiKey())
                .modelName(modelName)
                .dimension(dimension)
                .build();
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#embeddingModelNameProvider")
    void should_embed_one_text(String modelName) {
        EmbeddingModel model = getModel(modelName);
        Embedding embedding = model.embed("hello").content();

        assertThat(embedding.vector()).isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#embeddingModelNameProvider")
    void should_embed_one_text_by_customized_request(String modelName) {
        QwenEmbeddingModel model = getModel(modelName);

        Embedding sensitiveWordEmbedding =
                model.embed("(this is a sensitive word)").content();

        model.setTextEmbeddingParamCustomizer(textEmbeddingParamBuilder -> {
            textEmbeddingParamBuilder.clearTexts();
            textEmbeddingParamBuilder.text("(this is a desensitized word)");
        });

        Embedding desensitizedWordEmbedding =
                model.embed("(this is a sensitive word)").content();

        assertThat(desensitizedWordEmbedding).isNotEqualTo(sensitiveWordEmbedding);
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#embeddingModelNameProvider")
    void should_embed_documents(String modelName) {
        EmbeddingModel model = getModel(modelName);
        List<Embedding> embeddings = model.embedAll(asList(textSegment("hello"), textSegment("how are you?")))
                .content();

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0).vector()).isNotEmpty();
        assertThat(embeddings.get(1).vector()).isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#embeddingModelNameProvider")
    void should_embed_queries(String modelName) {
        EmbeddingModel model = getModel(modelName);
        List<Embedding> embeddings = model.embedAll(asList(
                        textSegment("hello", Metadata.from(TYPE_KEY, TYPE_QUERY)),
                        textSegment("how are you?", Metadata.from(TYPE_KEY, TYPE_QUERY))))
                .content();

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0).vector()).isNotEmpty();
        assertThat(embeddings.get(1).vector()).isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#embeddingModelNameProvider")
    void should_embed_mix_segments(String modelName) {
        EmbeddingModel model = getModel(modelName);
        List<Embedding> embeddings = model.embedAll(
                        asList(textSegment("hello", Metadata.from(TYPE_KEY, TYPE_QUERY)), textSegment("how are you?")))
                .content();

        assertThat(embeddings).hasSize(2);
        assertThat(embeddings.get(0).vector()).isNotEmpty();
        assertThat(embeddings.get(1).vector()).isNotEmpty();
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#embeddingModelNameProvider")
    void should_embed_large_amounts_of_documents(String modelName) {
        EmbeddingModel model = getModel(modelName);
        List<Embedding> embeddings =
                model.embedAll(Collections.nCopies(50, textSegment("hello"))).content();

        assertThat(embeddings).hasSize(50);
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#embeddingModelNameProvider")
    void should_embed_large_amounts_of_queries(String modelName) {
        EmbeddingModel model = getModel(modelName);
        List<Embedding> embeddings = model.embedAll(
                        Collections.nCopies(50, textSegment("hello", Metadata.from(TYPE_KEY, TYPE_QUERY))))
                .content();

        assertThat(embeddings).hasSize(50);
    }

    @ParameterizedTest
    @MethodSource("dev.langchain4j.community.model.dashscope.QwenTestHelper#embeddingModelNameProvider")
    void should_embed_large_amounts_of_mix_segments(String modelName) {
        EmbeddingModel model = getModel(modelName);
        List<Embedding> embeddings = model.embedAll(Stream.concat(
                                Collections.nCopies(50, textSegment("hello", Metadata.from(TYPE_KEY, TYPE_QUERY)))
                                        .stream(),
                                Collections.nCopies(50, textSegment("how are you?")).stream())
                        .collect(Collectors.toList()))
                .content();

        assertThat(embeddings).hasSize(100);
    }

    @Test
    void should_embed_one_text_by_customized_dimension() {
        assertThatThrownBy(() -> getModel(TEXT_EMBEDDING_V1, 512)).isExactlyInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> getModel(TEXT_EMBEDDING_V2, 512)).isExactlyInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> getModel(TEXT_EMBEDDING_V3, 510)).isExactlyInstanceOf(IllegalArgumentException.class);

        QwenEmbeddingModel model = getModel(TEXT_EMBEDDING_V3, 512);
        assertThat(model.dimension()).isEqualTo(512);

        Embedding embedding = model.embed("hello").content();
        assertThat(embedding.dimension()).isEqualTo(512);
    }
}
