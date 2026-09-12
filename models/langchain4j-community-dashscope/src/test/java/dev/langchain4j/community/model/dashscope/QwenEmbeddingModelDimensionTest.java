package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenModelName.TEXT_EMBEDDING_V1;
import static dev.langchain4j.community.model.dashscope.QwenModelName.TEXT_EMBEDDING_V2;
import static dev.langchain4j.community.model.dashscope.QwenModelName.TEXT_EMBEDDING_V3;
import static dev.langchain4j.community.model.dashscope.QwenModelName.TEXT_EMBEDDING_V4;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Unit tests for QwenEmbeddingModel dimension validation.
 * These tests verify that custom dimensions are accepted for all embedding model versions (API validates).
 */
class QwenEmbeddingModelDimensionTest {

    private static final String API_KEY = "test-api-key";

    private QwenEmbeddingModel buildModel(String modelName, Integer dimension) {
        return QwenEmbeddingModel.builder()
                .apiKey(API_KEY)
                .modelName(modelName)
                .dimension(dimension)
                .build();
    }

    // --- V1 and V2: accept dimensions (API validates) ---

    static Stream<Integer> supportedDimensionsV1AndV2() {
        return Stream.of(512, 768, 1024, 1536, 2048);
    }

    @ParameterizedTest
    @MethodSource("supportedDimensionsV1AndV2")
    void should_accept_dimension_for_v1(Integer dimension) {
        QwenEmbeddingModel model = buildModel(TEXT_EMBEDDING_V1, dimension);
        assertThat(model.dimension()).isEqualTo(dimension);
    }

    @ParameterizedTest
    @MethodSource("supportedDimensionsV1AndV2")
    void should_accept_dimension_for_v2(Integer dimension) {
        QwenEmbeddingModel model = buildModel(TEXT_EMBEDDING_V2, dimension);
        assertThat(model.dimension()).isEqualTo(dimension);
    }

    // --- V3: accept all dimensions (API validates) ---

    static Stream<Integer> supportedDimensionsV3() {
        return Stream.of(64, 128, 256, 512, 768, 1024, 1536, 2048);
    }

    @ParameterizedTest
    @MethodSource("supportedDimensionsV3")
    void should_accept_dimension_for_v3(Integer dimension) {
        QwenEmbeddingModel model = buildModel(TEXT_EMBEDDING_V3, dimension);
        assertThat(model.dimension()).isEqualTo(dimension);
    }

    // --- V4: accept all dimensions (API validates) ---

    static Stream<Integer> supportedDimensionsV4() {
        return Stream.of(64, 128, 256, 512, 768, 1024, 1536, 2048);
    }

    @ParameterizedTest
    @MethodSource("supportedDimensionsV4")
    void should_accept_dimension_for_v4(Integer dimension) {
        QwenEmbeddingModel model = buildModel(TEXT_EMBEDDING_V4, dimension);
        assertThat(model.dimension()).isEqualTo(dimension);
    }
}
