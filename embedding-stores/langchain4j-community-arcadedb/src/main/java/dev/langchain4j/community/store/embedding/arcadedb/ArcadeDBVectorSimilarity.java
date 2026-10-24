package dev.langchain4j.community.store.embedding.arcadedb;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.RelevanceScore;
import java.util.Locale;

/** The similarity functions of ArcadeDB's {@code LSM_VECTOR} index and the relevance score reported for each. */
enum ArcadeDBVectorSimilarity {
    /** Score {@code (1 + cosine) / 2}. */
    COSINE,
    /** Score {@code 1 / (1 + distance)}. */
    EUCLIDEAN,
    /** For unit-length vectors (as ArcadeDB requires): score {@code (1 + dot) / 2}, clamped to [0, 1]. */
    DOT_PRODUCT;

    static ArcadeDBVectorSimilarity of(String name) {
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "Unsupported similarity function '" + name + "'; use COSINE, EUCLIDEAN or DOT_PRODUCT");
        }
    }

    /** Relevance score in [0, 1]; higher is more similar, monotonic in the index's own ordering. */
    double relevance(Embedding query, Embedding embedding) {
        float[] a = query.vector();
        float[] b = embedding.vector();
        return switch (this) {
            case COSINE -> RelevanceScore.fromCosineSimilarity(CosineSimilarity.between(query, embedding));
            case EUCLIDEAN -> {
                double sum = 0;
                for (int i = 0; i < a.length; i++) {
                    double d = a[i] - b[i];
                    sum += d * d;
                }
                yield 1.0 / (1.0 + Math.sqrt(sum));
            }
            case DOT_PRODUCT -> {
                double dot = 0;
                for (int i = 0; i < a.length; i++) {
                    dot += a[i] * b[i];
                }
                yield Math.max(0.0, Math.min(1.0, (1.0 + dot) / 2.0));
            }
        };
    }
}
