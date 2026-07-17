package dev.langchain4j.community.model.zhipu;

import static dev.langchain4j.community.model.zhipu.embedding.EmbeddingModel.EMBEDDING_3;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ZHIPU_API_KEY", matches = ".+")
class ZhipuAiEmbeddingModelIT {
    private static final String apiKey = System.getenv("ZHIPU_API_KEY");

    ZhipuAiEmbeddingModel model = ZhipuAiEmbeddingModel.builder()
            .model(EMBEDDING_3)
            .apiKey(apiKey)
            .logRequests(true)
            .logResponses(true)
            .maxRetries(1)
            .build();

    @Test
    void should_embed_and_return_token_usage() {

        // given
        String text = "hello world";

        // when
        Response<Embedding> response = model.embed(text);

        assertThat(response.content().dimension()).isEqualTo(2048);
        // then
        TokenUsage tokenUsage = response.tokenUsage();

        assertThat(tokenUsage.inputTokenCount()).isEqualTo(5);
        assertThat(tokenUsage.outputTokenCount()).isZero();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(5);
        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_embed_in_batches() {

        int batchSize = 10;
        int numberOfSegments = batchSize + 1;

        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < numberOfSegments; i++) {
            segments.add(TextSegment.from("text " + i));
        }

        Response<List<Embedding>> response = model.embedAll(segments);

        assertThat(response.content()).hasSize(11);
        assertThat(response.content().get(0).dimension()).isEqualTo(2048);

        TokenUsage tokenUsage = response.tokenUsage();
        assertThat(tokenUsage.inputTokenCount()).isEqualTo(67);
        assertThat(tokenUsage.outputTokenCount()).isZero();
        assertThat(tokenUsage.totalTokenCount()).isEqualTo(67);

        assertThat(response.finishReason()).isNull();
    }

    @Test
    void should_embed_in_batches_by_dimensions() {
        ZhipuAiEmbeddingModel model_v3 = ZhipuAiEmbeddingModel.builder()
                .model(EMBEDDING_3)
                .apiKey(apiKey)
                .logRequests(true)
                .logResponses(true)
                .maxRetries(1)
                .dimensions(512)
                .build();

        String text = "hello world";
        Response<Embedding> response = model_v3.embed(text);
        assertThat(response.content().dimension()).isEqualTo(512);
    }
}
