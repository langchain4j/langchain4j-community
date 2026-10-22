package dev.langchain4j.community.store.embedding.arcadedb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

/** Shared check for the {@code efSearch} option: a wider beam never lowers recall against exact search. */
final class ArcadeDBEfSearchTestHelper {

    static final int DIMENSION = 32;
    static final int VECTORS = 3000;
    private static final int QUERIES = 25;
    private static final int K = 10;

    private ArcadeDBEfSearchTestHelper() {}

    /** Loads the vectors through {@code stores.apply(1)} and searches them with every beam width in {@code beams}. */
    static void assertWiderBeamNeverLowersRecall(IntFunction<ArcadeDBEmbeddingStore> stores, int... beams) {
        Random random = new Random(42);
        List<float[]> vectors = new ArrayList<>();
        for (int i = 0; i < VECTORS; i++) {
            vectors.add(randomUnitVector(random));
        }
        ArcadeDBEmbeddingStore loader = stores.apply(beams[0]);
        List<String> ids = loader.addAll(vectors.stream().map(Embedding::new).toList());

        List<float[]> queries = IntStream.range(0, QUERIES)
                .mapToObj(i -> randomUnitVector(random))
                .toList();
        double previous = -1;
        for (int beam : beams) {
            ArcadeDBEmbeddingStore store = stores.apply(beam);
            double recall = 0;
            for (float[] query : queries) {
                Set<String> exact = exactTopK(vectors, ids, query);
                List<EmbeddingMatch<TextSegment>> matches = store.search(EmbeddingSearchRequest.builder()
                                .queryEmbedding(new Embedding(query))
                                .maxResults(K)
                                .build())
                        .matches();
                recall += matches.stream()
                                .filter(m -> exact.contains(m.embeddingId()))
                                .count()
                        / (double) K;
            }
            recall /= QUERIES;
            assertThat(recall).as("recall@%d with efSearch %d", K, beam).isGreaterThanOrEqualTo(previous);
            previous = recall;
        }
        assertThat(previous).isGreaterThanOrEqualTo(0.9);
    }

    private static Set<String> exactTopK(List<float[]> vectors, List<String> ids, float[] query) {
        List<Integer> order =
                new ArrayList<>(IntStream.range(0, vectors.size()).boxed().toList());
        order.sort(Comparator.comparingDouble(i -> -dot(vectors.get(i), query)));
        Set<String> top = new HashSet<>();
        for (int i = 0; i < K; i++) {
            top.add(ids.get(order.get(i)));
        }
        return top;
    }

    private static double dot(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    private static float[] randomUnitVector(Random random) {
        float[] v = new float[DIMENSION];
        double norm = 0;
        for (int i = 0; i < DIMENSION; i++) {
            v[i] = (float) random.nextGaussian();
            norm += v[i] * v[i];
        }
        for (int i = 0; i < DIMENSION; i++) {
            v[i] /= (float) Math.sqrt(norm);
        }
        return v;
    }
}
