package dev.langchain4j.community.store.embedding.arcadedb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Shared checks for the vector index options (similarity function, quantization) in both modes. */
final class ArcadeDBIndexOptionsTestHelper {

    static final int SIMILARITY_DIMENSION = 4;
    static final int QUANTIZATION_DIMENSION = 32;

    private ArcadeDBIndexOptionsTestHelper() {}

    /** Three unit vectors at cosine 1, 0.8 and 0 from the query: same order for every function, scores per function. */
    static void assertSimilarityScores(ArcadeDBEmbeddingStore store, String similarity) {
        String same = store.add(new Embedding(new float[] {1, 0, 0, 0}));
        String near = store.add(new Embedding(new float[] {0.8f, 0.6f, 0, 0}));
        String far = store.add(new Embedding(new float[] {0, 1, 0, 0}));

        List<EmbeddingMatch<TextSegment>> matches = store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(new Embedding(new float[] {1, 0, 0, 0}))
                        .maxResults(3)
                        .build())
                .matches();

        assertThat(matches).extracting(EmbeddingMatch::embeddingId).containsExactly(same, near, far);
        double[] expected =
                switch (similarity) {
                    case "EUCLIDEAN" -> new double[] {1, 1 / (1 + Math.sqrt(0.4)), 1 / (1 + Math.sqrt(2))};
                    default -> new double[] {1, 0.9, 0.5};
                };
        for (int i = 0; i < 3; i++) {
            assertThat(matches.get(i).score()).isCloseTo(expected[i], within(1e-4));
        }
    }

    /** A stored vector is found among the 10 nearest neighbours of itself. */
    static void assertFindsStoredVector(ArcadeDBEmbeddingStore store) {
        Random random = new Random(7);
        List<Embedding> embeddings = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            float[] v = new float[QUANTIZATION_DIMENSION];
            double norm = 0;
            for (int d = 0; d < v.length; d++) {
                v[d] = (float) random.nextGaussian();
                norm += v[d] * v[d];
            }
            for (int d = 0; d < v.length; d++) {
                v[d] /= (float) Math.sqrt(norm);
            }
            embeddings.add(new Embedding(v));
        }
        List<String> ids = store.addAll(embeddings);

        List<EmbeddingMatch<TextSegment>> matches = store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(embeddings.get(7))
                        .maxResults(10)
                        .build())
                .matches();

        assertThat(matches).hasSize(10);
        assertThat(matches).extracting(EmbeddingMatch::embeddingId).contains(ids.get(7));
    }
}
