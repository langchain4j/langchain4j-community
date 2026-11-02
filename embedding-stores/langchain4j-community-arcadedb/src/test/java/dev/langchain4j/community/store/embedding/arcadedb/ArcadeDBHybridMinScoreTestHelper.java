package dev.langchain4j.community.store.embedding.arcadedb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.util.List;

/**
 * Shared check that hybrid search with a {@code minScore} returns the matching records even when records below the
 * minimum score rank above them in the fused order (they match the query text, the matching ones do not).
 */
final class ArcadeDBHybridMinScoreTestHelper {

    static final int DIMENSION = 2;
    private static final Embedding QUERY = new Embedding(new float[] {1, 0});

    private ArcadeDBHybridMinScoreTestHelper() {}

    /** The reproduction from the review of #883: one record passes minScore, one ranks above it and does not. */
    static void assertOneMatchIsNotDropped(ArcadeDBEmbeddingStore store) {
        String a = store.add(new Embedding(new float[] {1, 0}), TextSegment.from("an apple orchard"));
        store.add(new Embedding(new float[] {0, 1}), TextSegment.from("zebra"));

        assertThat(search(store, 1)).containsExactly(a);
    }

    /** More records below minScore than requested results rank above the matching ones. */
    static void assertSeveralMatchesAreNotDropped(ArcadeDBEmbeddingStore store) {
        String a1 = store.add(new Embedding(new float[] {1, 0}), TextSegment.from("an apple orchard"));
        String a2 = store.add(new Embedding(new float[] {0.99f, 0.14f}), TextSegment.from("a pear orchard"));
        for (int i = 0; i < 3; i++) {
            store.add(new Embedding(new float[] {0, 1}), TextSegment.from("zebra number " + i));
        }

        assertThat(search(store, 2)).containsExactly(a1, a2);
    }

    private static List<String> search(ArcadeDBEmbeddingStore store, int maxResults) {
        return store
                .search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(QUERY)
                        .query("zebra")
                        .maxResults(maxResults)
                        .minScore(0.6)
                        .build())
                .matches()
                .stream()
                .map(EmbeddingMatch::embeddingId)
                .toList();
    }
}
