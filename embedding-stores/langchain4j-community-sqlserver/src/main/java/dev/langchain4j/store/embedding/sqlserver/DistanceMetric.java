package dev.langchain4j.store.embedding.sqlserver;

/**
 * Enum representing different distance metrics that can be used for
 * similarity and relevance computations.
 */
public enum DistanceMetric {

    /**
     * Cosine distance metric.
     */
    COSINE("cosine"),
    /**
     * Euclidean distance metric.
     */
    EUCLIDEAN("euclidean");

    private final String metric;

    private DistanceMetric(String name) {
        this.metric = name;
    }

    /**
     * Returns the name of the metric.
     * @return the name of the metric
     */
    public String getMetric() {
        return metric;
    }

    /**
     * Converts distance returned by SQL Server VECTOR_DISTANCE function to relevance score.
     * Each metric uses its own conversion formula to map to [0-2] range:
     * - COSINE: relevance = 2 - distance (distance is between 0 and 2)
     * - EUCLIDEAN: uses exponential decay to convert [0, +∞] to [0-2] range
     *
     * @param distance the distance value returned by VECTOR_DISTANCE
     * @return the relevance score in [0-2] range using metric-specific conversion
     */
    public double distanceToScore(double distance) {
        return switch (this) {
            case COSINE ->
                // For cosine: distance is in [0, 2], simply return 2 - distance
                2.0 - distance;
            case EUCLIDEAN ->
                // For euclidean: distance is in [0, +∞], use exponential decay
                // Formula: 2 * e^(-distance) maps [0, +∞] to [0, 2]
                2.0 * Math.exp(-distance);
            default -> throw new UnsupportedOperationException("Unsupported distance metric: " + this.metric);
        };
    }
}
