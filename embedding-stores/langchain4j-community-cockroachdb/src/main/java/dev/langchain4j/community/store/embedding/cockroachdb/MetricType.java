package dev.langchain4j.community.store.embedding.cockroachdb;

/**
 * Distance metric for vector similarity search.
 *
 * <p>CockroachDB's {@code CREATE VECTOR INDEX} does NOT bind a metric to the index;
 * the metric is selected at query time by the operator. Mapping:
 *
 * <ul>
 *   <li>{@link #COSINE} → {@code <=>}</li>
 *   <li>{@link #EUCLIDEAN} → {@code <->}</li>
 *   <li>{@link #DOT_PRODUCT} → {@code <#>}</li>
 * </ul>
 */
public enum MetricType {
    COSINE,
    EUCLIDEAN,
    DOT_PRODUCT;

    public String operator() {
        switch (this) {
            case COSINE:
                return "<=>";
            case EUCLIDEAN:
                return "<->";
            case DOT_PRODUCT:
                return "<#>";
            default:
                return "<=>";
        }
    }
}
