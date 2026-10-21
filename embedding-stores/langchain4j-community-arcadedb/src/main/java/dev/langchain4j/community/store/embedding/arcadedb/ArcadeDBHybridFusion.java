package dev.langchain4j.community.store.embedding.arcadedb;

/**
 * How a hybrid search combines the dense (vector) and full-text (BM25) rankings, using ArcadeDB's
 * {@code vector.fuse()}.
 */
public enum ArcadeDBHybridFusion {
    /** Reciprocal rank fusion: each source contributes {@code weight / (k + rank)}. */
    RRF,
    /** Weighted sum of the min-max normalised scores of the two sources. */
    LINEAR,
    /** Distribution-based score fusion: scores normalised by mean and standard deviation of each source. */
    DBSF
}
