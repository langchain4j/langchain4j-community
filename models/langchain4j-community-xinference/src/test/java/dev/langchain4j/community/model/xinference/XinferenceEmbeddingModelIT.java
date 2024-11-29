package dev.langchain4j.community.model.xinference;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class XinferenceEmbeddingModelIT extends AbstractXinferenceEmbeddingModelInfrastructure {

    EmbeddingModel model = XinferenceEmbeddingModel.builder()
            .baseUrl(baseUrl())
            .apiKey(apiKey())
            .modelName(modelName())
            .logRequests(true)
            .logResponses(true)
            .build();

    @Test
    void should_embed_single_text() {

        // given
        String text = "hello world";

        // when
        Response<Embedding> response = model.embed(text);

        // then
        assertThat(response.content().vector()).isNotEmpty();
        assertThat(response.content().dimension()).isEqualTo(model.dimension());

        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_embed_multiple_segments() {

        // given
        List<TextSegment> segments = asList(
                TextSegment.from("hello"),
                TextSegment.from("world")
        );

        // when
        Response<List<Embedding>> response = model.embedAll(segments);

        // then
        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).dimension()).isEqualTo(model.dimension());
        assertThat(response.content().get(1).dimension()).isEqualTo(model.dimension());

        assertThat(response.tokenUsage()).isNotNull();
        assertThat(response.finishReason()).isNull();
    }
}
