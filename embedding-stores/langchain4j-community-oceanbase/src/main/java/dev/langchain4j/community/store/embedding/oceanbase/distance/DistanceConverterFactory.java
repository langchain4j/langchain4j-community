package dev.langchain4j.community.store.embedding.oceanbase.distance;

/**
 * Factory for creating appropriate distance converters based on the metric type.
 * Implements the Factory Method pattern.
 */
public class DistanceConverterFactory {

    /**
     * Returns the appropriate converter for the given distance metric.
     *
     * @param metric The distance metric name (e.g., "cosine", "manhattan", "euclidean")
     * @return A DistanceConverter appropriate for the given metric
     */
    public static DistanceConverter getConverter(String metric) {
        if (metric == null) {
            return new EuclideanDistanceConverter();
        }

        metric = metric.toLowerCase();

        switch (metric) {
            case "l1":
            case "manhattan":
                return new ManhattanDistanceConverter();
            case "l2":
            case "euclidean":
                return new EuclideanDistanceConverter();
            default:
                return new CosineDistanceConverter();
        }
    }
}
