package dev.langchain4j.community.store.embedding.redis;

import static dev.langchain4j.community.store.embedding.redis.MetricType.COSINE;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm.HNSW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import redis.clients.jedis.json.Path2;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;
import redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm;

/**
 * Redis Schema Description
 */
public class RedisSchema {

    public static final String SCORE_FIELD_NAME = "vector_score";
    public static final String JSON_KEY = "$";
    public static final Path2 JSON_SET_PATH = Path2.of(JSON_KEY);
    public static final String JSON_PATH_PREFIX = "$.";
    private static final VectorAlgorithm DEFAULT_VECTOR_ALGORITHM = HNSW;
    private static final MetricType DEFAULT_METRIC_TYPE = COSINE;

    /* Redis schema field settings */

    private final String indexName;
    private final String prefix;
    private final String vectorFieldName;
    private final String scalarFieldName;
    private final Map<String, SchemaField> metadataConfig;

    /* Vector field settings */

    private final VectorAlgorithm vectorAlgorithm;
    private final Integer dimension;
    private final MetricType metricType;

    private RedisSchema(Builder builder) {
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

    SchemaField[] toSchemaFields() {
        Map<String, Object> vectorAttrs = new HashMap<>();
        vectorAttrs.put("DIM", dimension);
        vectorAttrs.put("DISTANCE_METRIC", metricType.name());
        vectorAttrs.put("TYPE", "FLOAT32");
        vectorAttrs.put("INITIAL_CAP", 5);
        List<SchemaField> fields = new ArrayList<>();
        fields.add(TextField.of(JSON_PATH_PREFIX + scalarFieldName)
                .as(scalarFieldName)
                .weight(1.0));
        fields.add(VectorField.builder()
                .fieldName(JSON_PATH_PREFIX + vectorFieldName)
                .algorithm(vectorAlgorithm)
                .attributes(vectorAttrs)
                .as(vectorFieldName)
                .build());
        // Add Metadata fields
        fields.addAll(metadataConfig.values());

        return fields.toArray(new SchemaField[0]);
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

    public Map<String, SchemaField> getMetadataConfig() {
        return metadataConfig;
    }

    public VectorAlgorithm getVectorAlgorithm() {
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
        private Map<String, SchemaField> metadataConfig = new HashMap<>();

        /* Vector field settings */

        private VectorAlgorithm vectorAlgorithm = DEFAULT_VECTOR_ALGORITHM;
        private Integer dimension;
        private final MetricType metricType = DEFAULT_METRIC_TYPE;

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

        public Builder vectorAlgorithm(VectorAlgorithm vectorAlgorithm) {
            this.vectorAlgorithm = vectorAlgorithm;
            return this;
        }

        public Builder dimension(Integer dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder metadataConfig(Map<String, SchemaField> metadataConfig) {
            this.metadataConfig = metadataConfig;
            return this;
        }

        public RedisSchema build() {
            return new RedisSchema(this);
        }
    }
}
