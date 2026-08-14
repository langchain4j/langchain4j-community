package dev.langchain4j.community.store.embedding.valkey;

/**
 * Similarity metric used by Valkey vector search.
 */
public enum MetricType {

    /**
     * Cosine similarity.
     */
    COSINE,

    /**
     * Inner product.
     */
    IP,

    /**
     * Euclidean distance.
     */
    L2
}
