package dev.langchain4j.community.store.embedding.yugabytedb.index;

import dev.langchain4j.community.store.embedding.yugabytedb.MetricType;
import java.util.Collections;
import java.util.List;

/**
 * Represents no vector index - performs sequential scans for similarity search.
 *
 * Use this when:
 * - Dataset is very small (&lt; 10,000 vectors)
 * - Index creation time is a concern
 * - Testing or development without index overhead
 *
 * Note: Sequential scan can be acceptably fast for small datasets
 * but becomes very slow as the dataset grows.
 *
 * Example usage:
 * <pre>
 * YugabyteDBSchema schema = YugabyteDBSchema.builder()
 *     .vectorIndex(new NoIndex())
 *     .build();
 * </pre>
 */
public class NoIndex implements BaseIndex {

    private static final String INDEX_TYPE = "none";

    /**
     * Default constructor.
     */
    public NoIndex() {
        // No configuration needed for no index
    }

    @Override
    public String getIndexOptions() {
        return "";
    }

    @Override
    public MetricType getMetricType() {
        return MetricType.COSINE; // Default, not actually used without index
    }

    @Override
    public String getName() {
        return null; // No name needed when no index is created
    }

    @Override
    public String getIndexType() {
        return INDEX_TYPE;
    }

    @Override
    public List<String> getPartialIndexes() {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        return "NoIndex (sequential scan)";
    }
}
