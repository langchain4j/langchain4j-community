package dev.langchain4j.community.store.embedding.yugabytedb.index;

import dev.langchain4j.community.store.embedding.yugabytedb.MetricType;
import java.util.List;

/**
 * Base interface for YugabyteDB vector indexes.
 *
 * <p>Provides a type-safe way to configure vector indexes with their specific parameters.</p>
 *
 * <p><b>YugabyteDB supported index types:</b></p>
 * <ul>
 *   <li><b>ybhnsw</b> - YugabyteDB's HNSW implementation (recommended)</li>
 *   <li><b>none</b> - No index, uses sequential scan</li>
 * </ul>
 *
 * <p><b>Note:</b> YugabyteDB does NOT support IVFFlat. Only ybhnsw is available.</p>
 */
public interface BaseIndex {

    /**
     * Default suffix for index names
     */
    String DEFAULT_INDEX_NAME_SUFFIX = "langchain4j_vector_index";

    /**
     * Get the index-specific options string for CREATE INDEX statement.
     *
     * For example:
     * - HNSW (ybhnsw): "(m = 16, ef_construction = 64)"
     * - NoIndex: null
     *
     * @return index options string, or null if no options
     */
    String getIndexOptions();

    /**
     * Get the distance metric type for this index.
     *
     * @return the metric type (COSINE, EUCLIDEAN, DOT_PRODUCT)
     */
    MetricType getMetricType();

    /**
     * Get the name of this index.
     * If not specified, a default name will be generated.
     *
     * @return index name, or null to auto-generate
     */
    String getName();

    /**
     * Get the YugabyteDB index type.
     *
     * @return the index type string ("ybhnsw" or "none")
     */
    String getIndexType();

    /**
     * Get partial index conditions if any.
     * Partial indexes only index rows that match a WHERE condition.
     *
     * @return list of partial index WHERE conditions, or empty list
     */
    List<String> getPartialIndexes();
}
