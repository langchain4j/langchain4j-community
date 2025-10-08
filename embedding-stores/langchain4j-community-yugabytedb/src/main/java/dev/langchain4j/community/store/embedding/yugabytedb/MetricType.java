package dev.langchain4j.community.store.embedding.yugabytedb;

/**
 * Similarity metric types supported by YugabyteDB with pgvector extension
 *
 * These metrics define how vector similarity is calculated during embedding searches.
 * Each metric has different characteristics and is suitable for different use cases.
 */
public enum MetricType {

    /**
     * Cosine similarity
     *
     * Measures the cosine of the angle between two vectors.
     * Range: [-1, 1] where 1 means identical direction, 0 means orthogonal, -1 means opposite.
     * Best for: normalized vectors, text embeddings, semantic similarity
     */
    COSINE,

    /**
     * Euclidean distance (L2 distance)
     *
     * Measures the straight-line distance between two points in vector space.
     * Range: [0, ∞) where 0 means identical vectors, larger values mean more different.
     * Best for: when vector magnitude matters, spatial data, image embeddings
     */
    EUCLIDEAN,

    /**
     * Dot product (Inner product)
     *
     * Measures the dot product between two vectors.
     * Range: (-∞, ∞) where higher values indicate more similarity.
     * Best for: when both direction and magnitude matter, recommendation systems
     */
    DOT_PRODUCT
}
