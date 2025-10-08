package dev.langchain4j.community.store.embedding.yugabytedb.index;

import dev.langchain4j.community.store.embedding.yugabytedb.MetricType;
import java.util.ArrayList;
import java.util.List;

/**
 * HNSW (Hierarchical Navigable Small World) Index for YugabyteDB.
 *
 * <p>YugabyteDB implements HNSW as <b>ybhnsw</b>, which provides fast approximate
 * nearest neighbor search with good recall. It builds a multi-layer graph structure
 * for efficient similarity search.</p>
 *
 * <p><b>Important:</b> YugabyteDB only supports HNSW (ybhnsw), not IVFFlat.</p>
 *
 * <p>Key parameters:</p>
 * <ul>
 * <li><b>m</b>: Maximum number of connections per layer (default: 16)
 *      Higher values = better recall but more memory</li>
 * <li><b>efConstruction</b>: Size of dynamic candidate list during construction (default: 64)
 *      Higher values = better index quality but slower build time</li>
 * </ul>
 *
 * <p>Example usage:</p>
 * <pre>
 * HNSWIndex index = HNSWIndex.builder()
 *     .name("my_hnsw_idx")
 *     .m(16)
 *     .efConstruction(64)
 *     .metricType(MetricType.COSINE)
 *     .build();
 * </pre>
 */
public class HNSWIndex implements BaseIndex {

    private static final String DEFAULT_INDEX_TYPE = "ybhnsw"; // YugabyteDB's HNSW implementation

    private final String name;
    private final String indexType;
    private final Integer m;
    private final Integer efConstruction;
    private final MetricType metricType;
    private final List<String> partialIndexes;

    /**
     * Constructor for HNSWIndex.
     * Use the builder() method to create instances.
     *
     * @param builder the builder with configuration
     */
    private HNSWIndex(Builder builder) {
        this.name = builder.name;
        this.indexType = builder.indexType != null ? builder.indexType : DEFAULT_INDEX_TYPE;
        this.m = builder.m;
        this.efConstruction = builder.efConstruction;
        this.metricType = builder.metricType;
        this.partialIndexes = builder.partialIndexes != null ? builder.partialIndexes : new ArrayList<>();
    }

    @Override
    public String getIndexOptions() {
        return String.format("(m = %d, ef_construction = %d)", m, efConstruction);
    }

    @Override
    public MetricType getMetricType() {
        return metricType;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getIndexType() {
        return indexType;
    }

    @Override
    public List<String> getPartialIndexes() {
        return partialIndexes;
    }

    /**
     * Get the maximum number of connections per layer.
     *
     * @return the m parameter
     */
    public Integer getM() {
        return m;
    }

    /**
     * Get the construction parameter.
     *
     * @return the efConstruction parameter
     */
    public Integer getEfConstruction() {
        return efConstruction;
    }

    /**
     * Create a new builder instance.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for configuring and creating HNSWIndex instances.
     */
    public static class Builder {

        private String name;
        private String indexType;
        private Integer m = 16;
        private Integer efConstruction = 64;
        private MetricType metricType = MetricType.COSINE;
        private List<String> partialIndexes;

        /**
         * Set the index name.
         * If not set, a default name will be auto-generated.
         *
         * @param name the index name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set the index type.
         *
         * @param indexType "hnsw" for standard PostgreSQL or "ybhnsw" for YugabyteDB (default: "ybhnsw")
         * @return this builder
         */
        public Builder indexType(String indexType) {
            this.indexType = indexType;
            return this;
        }

        /**
         * Set the maximum number of connections per layer.
         *
         * Higher values improve recall but increase memory usage and index size.
         * Typical range: 8-64, default: 16
         *
         * @param m the maximum connections (must be positive)
         * @return this builder
         */
        public Builder m(Integer m) {
            if (m != null && m <= 0) {
                throw new IllegalArgumentException("m must be positive");
            }
            this.m = m;
            return this;
        }

        /**
         * Set the size of dynamic candidate list during construction.
         *
         * Higher values improve index quality but slow down construction.
         * Should be at least equal to m, typically 2-4x larger.
         * Typical range: 32-200, default: 64
         *
         * @param efConstruction the construction parameter (must be positive)
         * @return this builder
         */
        public Builder efConstruction(Integer efConstruction) {
            if (efConstruction != null && efConstruction <= 0) {
                throw new IllegalArgumentException("efConstruction must be positive");
            }
            this.efConstruction = efConstruction;
            return this;
        }

        /**
         * Set the distance metric type.
         *
         * @param metricType the metric type (COSINE, EUCLIDEAN, or DOT_PRODUCT)
         * @return this builder
         */
        public Builder metricType(MetricType metricType) {
            this.metricType = metricType;
            return this;
        }

        /**
         * Set partial index conditions.
         * Partial indexes only index rows matching the WHERE conditions.
         *
         * @param partialIndexes list of WHERE conditions
         * @return this builder
         */
        public Builder partialIndexes(List<String> partialIndexes) {
            this.partialIndexes = partialIndexes;
            return this;
        }

        /**
         * Build the HNSWIndex with the configured parameters.
         *
         * @return a new HNSWIndex instance
         */
        public HNSWIndex build() {
            if (metricType == null) {
                throw new IllegalStateException("metricType must be set");
            }
            if (efConstruction < m) {
                throw new IllegalStateException("efConstruction should be >= m for optimal results");
            }
            return new HNSWIndex(this);
        }
    }
}
