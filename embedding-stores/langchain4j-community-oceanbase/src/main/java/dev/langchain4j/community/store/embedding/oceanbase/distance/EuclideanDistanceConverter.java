package dev.langchain4j.community.store.embedding.oceanbase.distance;

/**
 * Euclidean distance to similarity converter.
 * Uses exponential decay: similarity = e^(-distance)
 * This works well for most distance metrics where smaller distance means higher similarity.
 */
public class EuclideanDistanceConverter implements DistanceConverter {

    @Override
    public double toSimilarity(double distance) {
        return Math.exp(-distance);
    }
}
