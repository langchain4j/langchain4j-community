package dev.langchain4j.community.store.embedding.arcadedb;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shared check for selective metadata filters: 500 records in 100 buckets, a filter allowing one bucket (5 records)
 * and {@code maxResults} 5. A post-filter over the nearest candidates finds only the few allowed ones among them; the
 * SQL filter finds all five.
 */
final class ArcadeDBSelectiveFilterTestHelper {

    static final int DIMENSION = 32;
    private static final int RECORDS = 500;
    private static final int BUCKETS = 100;

    private ArcadeDBSelectiveFilterTestHelper() {}

    /** Loads the records and returns the ids in bucket 0. */
    static Set<String> load(ArcadeDBEmbeddingStore store) {
        Random random = new Random(11);
        List<Embedding> embeddings = new ArrayList<>();
        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < RECORDS; i++) {
            float[] v = new float[DIMENSION];
            for (int d = 0; d < DIMENSION; d++) {
                v[d] = (float) random.nextGaussian();
            }
            Embedding embedding = new Embedding(v);
            embedding.normalize();
            embeddings.add(embedding);
            segments.add(TextSegment.from(
                    "document number " + i + " about databases", new Metadata(Map.of("bucket", i % BUCKETS))));
        }
        List<String> ids = store.addAll(embeddings, segments);
        Set<String> bucketZero = new java.util.HashSet<>();
        for (int i = 0; i < RECORDS; i += BUCKETS) {
            bucketZero.add(ids.get(i));
        }
        return bucketZero;
    }

    /** Ids returned for the filter {@code bucket < 1}, dense ({@code text} null) or hybrid. */
    static Set<String> search(ArcadeDBEmbeddingStore store, String text) {
        Random random = new Random(99);
        float[] q = new float[DIMENSION];
        for (int d = 0; d < DIMENSION; d++) {
            q[d] = (float) random.nextGaussian();
        }
        Embedding query = new Embedding(q);
        query.normalize();
        List<EmbeddingMatch<TextSegment>> matches = store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(query)
                        .query(text)
                        .filter(metadataKey("bucket").isLessThan(1))
                        .maxResults(5)
                        .build())
                .matches();
        matches.forEach(
                m -> assertThat(m.embedded().metadata().getInteger("bucket")).isZero());
        return matches.stream().map(EmbeddingMatch::embeddingId).collect(Collectors.toSet());
    }

    /** Asserts the strategies' results on one loaded store per strategy. */
    static void assertStrategies(Function<ArcadeDBFilterStrategy, ArcadeDBEmbeddingStore> stores) {
        for (ArcadeDBFilterStrategy strategy : ArcadeDBFilterStrategy.values()) {
            ArcadeDBEmbeddingStore store = stores.apply(strategy);
            Set<String> bucketZero = load(store);
            for (String text : new String[] {null, "databases"}) {
                Set<String> found = search(store, text);
                String what = strategy + (text == null ? " dense" : " hybrid");
                if (strategy == ArcadeDBFilterStrategy.POST_FILTER) {
                    assertThat(found).as(what).hasSizeLessThan(bucketZero.size());
                } else {
                    assertThat(found).as(what).isEqualTo(bucketZero);
                }
            }
        }
    }
}
