package dev.langchain4j.community.store.embedding;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.List;
import org.assertj.core.api.WithAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MmrSelectorTest implements WithAssertions {

    @Test
    void should_throw_exception_when_query_embedding_is_null() {
        List<EmbeddingMatch<TextSegment>> candidates = createCandidates();

        assertThatThrownBy(() -> MmrSelector.select(null, candidates, 5, 0.7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Query embedding cannot be null");
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 1.1, -1.0, 2.0})
    void should_throw_exception_when_lambda_out_of_range(double lambda) {
        Embedding queryEmbedding = embedding(1.0f, 0.0f, 0.0f);
        List<EmbeddingMatch<TextSegment>> candidates = createCandidates();

        assertThatThrownBy(() -> MmrSelector.select(queryEmbedding, candidates, 5, lambda))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lambda must be between 0.0 and 1.0");
    }

    @Test
    void should_throw_exception_when_max_results_is_negative() {
        Embedding queryEmbedding = embedding(1.0f, 0.0f, 0.0f);
        List<EmbeddingMatch<TextSegment>> candidates = createCandidates();

        assertThatThrownBy(() -> MmrSelector.select(queryEmbedding, candidates, -1, 0.7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Max results cannot be negative");
    }

    @Test
    void should_return_empty_list_when_candidates_is_null() {
        Embedding queryEmbedding = embedding(1.0f, 0.0f, 0.0f);

        List<EmbeddingMatch<TextSegment>> result = MmrSelector.select(queryEmbedding, null, 5, 0.7);

        assertThat(result).isEmpty();
    }

    @Test
    void should_return_empty_list_when_candidates_is_empty() {
        Embedding queryEmbedding = embedding(1.0f, 0.0f, 0.0f);

        List<EmbeddingMatch<TextSegment>> result = MmrSelector.select(queryEmbedding, emptyList(), 5, 0.7);

        assertThat(result).isEmpty();
    }

    @Test
    void should_return_empty_list_when_max_results_is_zero() {
        Embedding queryEmbedding = embedding(1.0f, 0.0f, 0.0f);
        List<EmbeddingMatch<TextSegment>> candidates = createCandidates();

        List<EmbeddingMatch<TextSegment>> result = MmrSelector.select(queryEmbedding, candidates, 0, 0.7);

        assertThat(result).isEmpty();
    }

    @Test
    void should_return_all_candidates_when_max_results_exceeds_size() {
        Embedding queryEmbedding = embedding(1.0f, 0.0f, 0.0f);
        List<EmbeddingMatch<TextSegment>> candidates = createCandidates();

        List<EmbeddingMatch<TextSegment>> result = MmrSelector.select(queryEmbedding, candidates, 10, 0.7);

        assertThat(result).hasSameSizeAs(candidates);
        assertThat(result).containsExactlyInAnyOrderElementsOf(candidates);
    }

    @Test
    void should_prioritize_relevance_when_lambda_is_one() {
        Embedding queryEmbedding = embedding(1.0f, 0.0f, 0.0f);
        List<EmbeddingMatch<TextSegment>> candidates = asList(
                match(0.9, "most_relevant", embedding(0.9f, 0.1f, 0.0f)),
                match(0.8, "similar_to_most_relevant", embedding(0.85f, 0.1f, 0.05f)),
                match(0.6, "diverse_but_less_relevant", embedding(0.0f, 1.0f, 0.0f)));

        List<EmbeddingMatch<TextSegment>> result = MmrSelector.select(queryEmbedding, candidates, 2, 1.0);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).embedded().text()).isEqualTo("most_relevant");
        assertThat(result.get(1).embedded().text()).isEqualTo("similar_to_most_relevant");
    }

    @Test
    void should_prioritize_diversity_when_lambda_is_zero() {
        Embedding queryEmbedding = embedding(1.0f, 0.0f, 0.0f);
        List<EmbeddingMatch<TextSegment>> candidates = asList(
                match(0.9, "most_relevant", embedding(0.9f, 0.1f, 0.0f)),
                match(0.85, "similar_to_most_relevant", embedding(0.85f, 0.15f, 0.0f)),
                match(0.6, "diverse_but_less_relevant", embedding(0.0f, 0.0f, 1.0f)));

        List<EmbeddingMatch<TextSegment>> result = MmrSelector.select(queryEmbedding, candidates, 2, 0.0);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).embedded().text()).isEqualTo("most_relevant");
        assertThat(result.get(1).embedded().text()).isEqualTo("diverse_but_less_relevant");
    }

    @Test
    void should_balance_relevance_and_diversity() {
        // Given: Query "apple" (Vector: [1.0, 0.0, 0.0])
        Embedding queryEmbedding = embedding(1.0f, 0.0f, 0.0f);
        List<EmbeddingMatch<TextSegment>> candidates = asList(
                match(0.9, "red apple", embedding(0.9f, 0.1f, 0.0f)), // Most similar to query
                match(0.8, "green apple", embedding(0.8f, 0.2f, 0.0f)), // Similar to query and red apple
                match(0.7, "apple pie", embedding(0.7f, 0.3f, 0.0f)), // Similar to both above
                match(0.6, "yellow banana", embedding(0.0f, 0.8f, 0.1f)), // Less similar but diverse
                match(0.5, "banana smoothie", embedding(0.1f, 0.7f, 0.1f)) // Less similar, similar to banana
                );

        List<EmbeddingMatch<TextSegment>> result = MmrSelector.select(queryEmbedding, candidates, 3, 0.7);

        assertThat(result).hasSize(3);
        // First should be most relevant
        assertThat(result.get(0).embedded().text()).isEqualTo("red apple");
        // Second should be diverse from first (banana vs apple)
        assertThat(result.get(1).embedded().text()).isEqualTo("yellow banana");
        // Third should balance remaining relevance and diversity
        assertThat(result.get(2).embedded().text()).isEqualTo("green apple");
    }

    @Test
    void should_use_existing_scores_when_available() {
        Embedding queryEmbedding = embedding(1.0f, 0.0f, 0.0f);
        List<EmbeddingMatch<TextSegment>> candidates = asList(
                match(0.95, "high_score", embedding(0.5f, 0.5f, 0.0f)),
                match(0.85, "medium_score", embedding(0.0f, 1.0f, 0.0f)),
                match(0.75, "low_score", embedding(0.0f, 0.0f, 1.0f)));

        List<EmbeddingMatch<TextSegment>> result = MmrSelector.select(queryEmbedding, candidates, 2, 1.0);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).embedded().text()).isEqualTo("high_score");
        assertThat(result.get(1).embedded().text()).isEqualTo("medium_score");
    }

    @Test
    void should_handle_single_candidate() {
        Embedding queryEmbedding = embedding(1.0f, 0.0f, 0.0f);
        List<EmbeddingMatch<TextSegment>> candidates =
                asList(match(0.8, "only_candidate", embedding(0.8f, 0.2f, 0.0f)));

        List<EmbeddingMatch<TextSegment>> result = MmrSelector.select(queryEmbedding, candidates, 5, 0.7);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).embedded().text()).isEqualTo("only_candidate");
    }

    @Test
    void should_respect_max_results_limit() {
        Embedding queryEmbedding = embedding(1.0f, 0.0f, 0.0f);
        List<EmbeddingMatch<TextSegment>> candidates = createCandidates();

        List<EmbeddingMatch<TextSegment>> result = MmrSelector.select(queryEmbedding, candidates, 2, 0.7);

        assertThat(result).hasSize(2);
    }

    // Helper methods
    private Embedding embedding(float x, float y, float z) {
        return Embedding.from(new float[] {x, y, z});
    }

    private EmbeddingMatch<TextSegment> match(double score, String text, Embedding embedding) {
        return new EmbeddingMatch<>(score, text.hashCode() + "", embedding, TextSegment.from(text));
    }

    private List<EmbeddingMatch<TextSegment>> createCandidates() {
        return asList(
                match(0.9, "doc1", embedding(0.9f, 0.1f, 0.0f)),
                match(0.8, "doc2", embedding(0.8f, 0.2f, 0.0f)),
                match(0.7, "doc3", embedding(0.0f, 0.9f, 0.1f)),
                match(0.6, "doc4", embedding(0.1f, 0.1f, 0.8f)));
    }
}
