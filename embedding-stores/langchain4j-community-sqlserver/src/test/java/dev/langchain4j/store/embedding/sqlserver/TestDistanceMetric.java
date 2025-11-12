package dev.langchain4j.store.embedding.sqlserver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TestDistanceMetric {

    @Test
    void shouldConvertCosineDistanceToSimilarityScore() {
        // Test COSINE metric: relevance = 2 - distance
        assertEquals(2.0, DistanceMetric.COSINE.distanceToScore(0.0), 0.001); // Perfect similarity: 2 - 0 = 2
        assertEquals(1.0, DistanceMetric.COSINE.distanceToScore(1.0), 0.001); // Moderate similarity: 2 - 1 = 1
        assertEquals(0.0, DistanceMetric.COSINE.distanceToScore(2.0), 0.001); // No similarity: 2 - 2 = 0
        assertEquals(-1.0, DistanceMetric.COSINE.distanceToScore(3.0), 0.001); // Opposing vectors: 2 - 3 = -1
    }

    @Test
    void shouldConvertEuclideanDistanceToSimilarityScore() {
        // Test EUCLIDEAN metric: relevance = 2 * e^(-distance)
        assertEquals(
                2.0,
                DistanceMetric.EUCLIDEAN.distanceToScore(0.0),
                0.001); // Perfect similarity: 2 * e^(-0) = 2 * 1 = 2
        assertEquals(
                0.736, DistanceMetric.EUCLIDEAN.distanceToScore(1.0), 0.001); // Moderate similarity: 2 * e^(-1) ≈ 0.736
        assertEquals(
                0.100, DistanceMetric.EUCLIDEAN.distanceToScore(3.0), 0.001); // Lower similarity: 2 * e^(-3) ≈ 0.100
        assertEquals(
                0.013, DistanceMetric.EUCLIDEAN.distanceToScore(5.0), 0.001); // Very low similarity: 2 * e^(-5) ≈ 0.013
    }
}
