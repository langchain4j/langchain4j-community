package dev.langchain4j.community.store.embedding.valkey;

import static dev.langchain4j.community.store.embedding.valkey.MetricType.COSINE;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import glide.api.models.commands.FT.FTCreateOptions;
import glide.api.models.commands.FT.FTCreateOptions.DistanceMetric;
import glide.api.models.commands.FT.FTCreateOptions.FieldInfo;
import glide.api.models.commands.FT.FTCreateOptions.VectorFieldFlat;
import glide.api.models.commands.FT.FTCreateOptions.VectorFieldHnsw;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Valkey Schema Description
 */
public class ValkeySchema {

    public static final String JSON_KEY = "$";
    public static final String JSON_PATH_PREFIX = "$.";
    private static final String DEFAULT_VECTOR_ALGORITHM = "HNSW";
    private static final MetricType DEFAULT_METRIC_TYPE = COSINE;

    /* Valkey schema field settings */

    private final String indexName;
    private final String prefix;
    private final String vectorFieldName;
    private final String scalarFieldName;
    private final Map<String, FieldInfo> metadataConfig;

    /* Vector field settings */

    private final String vectorAlgorithm;
    private final Integer dimension;
    private final MetricType metricType;

    private ValkeySchema(Builder builder) {
        ensureTrue(builder.prefix.endsWith(":"), "Prefix should end with a ':'");

        this.indexName = builder.indexName;
        this.prefix = builder.prefix;
        this.vectorFieldName = builder.vectorFieldName;
        this.scalarFieldName = builder.scalarFieldName;
        this.vectorAlgorithm = builder.vectorAlgorithm;
        this.dimension = builder.dimension;
        this.metricType = builder.metricType;
        this.metadataConfig = builder.metadataConfig;
    }

    /**
     * Returns the auto-generated score field name used by Valkey for KNN queries.
     * Valkey generates score field names following the pattern {@code __<vector_field_name>_score}.
     *
     * @return the score field name
     */
    public String getScoreFieldName() {
        return "__" + vectorFieldName + "_score";
    }

    /**
     * Converts the schema configuration into an array of {@link FieldInfo} for use with
     * {@code FT.CREATE}.
     *
     * @return array of field info entries for index creation
     */
    FieldInfo[] toFieldInfoArray() {
        DistanceMetric distanceMetric = toDistanceMetric(metricType);

        List<FieldInfo> fields = new ArrayList<>();

        // Vector field
        FTCreateOptions.Field vectorField;
        if ("FLAT".equalsIgnoreCase(vectorAlgorithm)) {
            vectorField = VectorFieldFlat.builder(distanceMetric, dimension)
                    .initialCapacity(5)
                    .build();
        } else {
            vectorField = VectorFieldHnsw.builder(distanceMetric, dimension)
                    .initialCapacity(5)
                    .build();
        }
        fields.add(new FieldInfo(JSON_PATH_PREFIX + vectorFieldName, vectorFieldName, vectorField));

        // Metadata fields
        fields.addAll(metadataConfig.values());

        return fields.toArray(new FieldInfo[0]);
    }

    private static DistanceMetric toDistanceMetric(MetricType metricType) {
        return switch (metricType) {
            case COSINE -> DistanceMetric.COSINE;
            case IP -> DistanceMetric.IP;
            case L2 -> DistanceMetric.L2;
        };
    }

    public String getIndexName() {
        return indexName;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getVectorFieldName() {
        return vectorFieldName;
    }

    public String getScalarFieldName() {
        return scalarFieldName;
    }

    public Map<String, FieldInfo> getMetadataConfig() {
        return java.util.Collections.unmodifiableMap(metadataConfig);
    }

    public String getVectorAlgorithm() {
        return vectorAlgorithm;
    }

    public Integer getDimension() {
        return dimension;
    }

    public MetricType getMetricType() {
        return metricType;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String indexName;
        private String prefix = "embedding:";
        private String vectorFieldName = "vector";
        private String scalarFieldName = "text";
        private Map<String, FieldInfo> metadataConfig = new HashMap<>();

        /* Vector field settings */

        private String vectorAlgorithm = DEFAULT_VECTOR_ALGORITHM;
        private Integer dimension;
        private MetricType metricType = DEFAULT_METRIC_TYPE;

        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder vectorFieldName(String vectorFieldName) {
            this.vectorFieldName = vectorFieldName;
            return this;
        }

        public Builder scalarFieldName(String scalarFieldName) {
            this.scalarFieldName = scalarFieldName;
            return this;
        }

        public Builder vectorAlgorithm(String vectorAlgorithm) {
            this.vectorAlgorithm = vectorAlgorithm;
            return this;
        }

        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder metricType(MetricType metricType) {
            this.metricType = metricType;
            return this;
        }

        public Builder metadataConfig(Map<String, FieldInfo> metadataConfig) {
            this.metadataConfig = metadataConfig;
            return this;
        }

        public ValkeySchema build() {
            return new ValkeySchema(this);
        }
    }
}
