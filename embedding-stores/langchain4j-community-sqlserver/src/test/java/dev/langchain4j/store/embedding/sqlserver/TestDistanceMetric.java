package dev.langchain4j.store.embedding.sqlserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class TestDistanceMetric {

    @Test
    void shouldConvertCosineDistanceToSimilarityScore() {
        // Test COSINE metric: relevance = 2 - distance
        assertThat(DistanceMetric.COSINE.distanceToScore(0.0))
                .isCloseTo(2.0, within(0.001)); // Perfect similarity: 2 - 0 = 2
        assertThat(DistanceMetric.COSINE.distanceToScore(1.0))
                .isCloseTo(1.0, within(0.001)); // Moderate similarity: 2 - 1 = 1
        assertThat(DistanceMetric.COSINE.distanceToScore(2.0))
                .isCloseTo(0.0, within(0.001)); // No similarity: 2 - 2 = 0
        assertThat(DistanceMetric.COSINE.distanceToScore(3.0))
                .isCloseTo(-1.0, within(0.001)); // Opposing vectors: 2 - 3 = -1
    }

    @Test
    void shouldConvertEuclideanDistanceToSimilarityScore() {
        // Test EUCLIDEAN metric: relevance = 2 * e^(-distance)
        assertThat(DistanceMetric.EUCLIDEAN.distanceToScore(0.0))
                .isCloseTo(2.0, within(0.001)); // Perfect similarity: 2 * e^(-0) = 2 * 1 = 2
        assertThat(DistanceMetric.EUCLIDEAN.distanceToScore(1.0))
                .isCloseTo(0.736, within(0.001)); // Moderate similarity: 2 * e^(-1) ≈ 0.736
        assertThat(DistanceMetric.EUCLIDEAN.distanceToScore(3.0))
                .isCloseTo(0.100, within(0.001)); // Lower similarity: 2 * e^(-3) ≈ 0.100
        assertThat(DistanceMetric.EUCLIDEAN.distanceToScore(5.0))
                .isCloseTo(0.013, within(0.001)); // Very low similarity: 2 * e^(-5) ≈ 0.013
    }
}
