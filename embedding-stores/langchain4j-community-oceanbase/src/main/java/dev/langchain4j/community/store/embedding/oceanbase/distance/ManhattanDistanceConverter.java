package dev.langchain4j.community.store.embedding.oceanbase.distance;

/**
 * Manhattan distance to similarity converter.
 * Uses a simple inverse function: similarity = 1 / (1 + distance)
 * This works well for Manhattan distance, which is the sum of absolute differences.
 */
public class ManhattanDistanceConverter implements DistanceConverter {

    @Override
    public double toSimilarity(double distance) {
        return 1.0 / (1.0 + distance);
    }
}
