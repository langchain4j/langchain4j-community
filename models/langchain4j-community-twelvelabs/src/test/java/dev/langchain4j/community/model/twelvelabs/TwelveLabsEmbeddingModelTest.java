package dev.langchain4j.community.model.twelvelabs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TwelveLabsEmbeddingModelTest {

    @Test
    void should_require_api_key() {
        assertThatThrownBy(() -> TwelveLabsEmbeddingModel.builder()
                        .modelName(TwelveLabsEmbeddingModelName.MARENGO_3)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiKey");
    }

    @Test
    void should_require_model_name() {
        assertThatThrownBy(
                        () -> TwelveLabsEmbeddingModel.builder().apiKey("test").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelName");
    }

    @Test
    void should_expose_known_dimension_without_calling_api() {
        assertThat(TwelveLabsEmbeddingModelName.knownDimension("marengo3.0")).isEqualTo(512);
        assertThat(TwelveLabsEmbeddingModelName.MARENGO_3.dimension()).isEqualTo(512);
        assertThat(TwelveLabsEmbeddingModelName.knownDimension("unknown-model")).isNull();
    }
}
