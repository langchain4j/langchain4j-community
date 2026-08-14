package dev.langchain4j.community.model.twelvelabs;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.CosineSimilarity;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "TWELVELABS_API_KEY", matches = ".+")
class TwelveLabsEmbeddingModelIT {

    private static EmbeddingModel model() {
        return TwelveLabsEmbeddingModel.builder()
                .apiKey(System.getenv("TWELVELABS_API_KEY"))
                .modelName(TwelveLabsEmbeddingModelName.MARENGO_3)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(false) // embeddings are huge in logs
                .build();
    }

    @Test
    void should_embed_single_text() {

        // given
        EmbeddingModel model = model();

        // when
        Response<Embedding> response = model.embed("Hello World");

        // then
        assertThat(response.content().dimension()).isEqualTo(model.dimension());
    }

    @Test
    void should_embed_multiple_segments() {

        // given
        EmbeddingModel model = model();

        TextSegment segment1 = TextSegment.from("a cat playing piano");
        TextSegment segment2 = TextSegment.from("a kitten at a keyboard");

        // when
        Response<List<Embedding>> response = model.embedAll(asList(segment1, segment2));

        // then
        assertThat(response.content()).hasSize(2);

        Embedding embedding1 = response.content().get(0);
        assertThat(embedding1.dimension()).isEqualTo(model.dimension());

        Embedding embedding2 = response.content().get(1);
        assertThat(embedding2.dimension()).isEqualTo(model.dimension());

        assertThat(CosineSimilarity.between(embedding1, embedding2)).isGreaterThan(0.5);
    }
}
