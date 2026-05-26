package dev.langchain4j.community.cockroachdb.spring;

import static dev.langchain4j.community.cockroachdb.spring.CockroachDbEmbeddingStoreProperties.PREFIX;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the CockroachDB embedding store auto-configuration.
 *
 * <p>Either {@link #connectionString} or the combination of
 * {@link #host}/{@link #port}/{@link #database}/{@link #username}/{@link #password}
 * must be provided.
 */
@ConfigurationProperties(prefix = PREFIX)
public class CockroachDbEmbeddingStoreProperties {

    static final String PREFIX = "langchain4j.community.cockroachdb";

    /**
     * {@code cockroachdb://}, {@code postgresql://}, or {@code jdbc:postgresql://} URL.
     */
    private String connectionString;

    private String host;
    private Integer port;
    private String database;
    private String username;
    private String password;
    private String sslMode;

    /**
     * Table name for embeddings. Defaults to {@code embeddings}.
     */
    private String tableName;

    /**
     * Database schema. Defaults to {@code public}.
     */
    private String schemaName;

    /**
     * Embedding vector dimension. Inferred from the {@code EmbeddingModel} bean when available.
     */
    private Integer dimension;

    /**
     * Distance metric: {@code COSINE} (default), {@code EUCLIDEAN}, or {@code DOT_PRODUCT}.
     */
    private String metricType;

    /**
     * Optional namespace column for multi-tenancy.
     */
    private String namespaceColumn;

    /**
     * Optional namespace value scoping reads and writes through this store.
     */
    private String namespace;

    /**
     * Per-query {@code vector_search_beam_size} override (C-SPANN only).
     */
    private Integer searchBeamSize;

    /**
     * Vector index to create: {@code cspann} or {@code none}. Defaults to {@code none}.
     */
    private String indexType;

    private Integer minPartitionSize;
    private Integer maxPartitionSize;

    public String getConnectionString() {
        return connectionString;
    }

    public void setConnectionString(String connectionString) {
        this.connectionString = connectionString;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSslMode() {
        return sslMode;
    }

    public void setSslMode(String sslMode) {
        this.sslMode = sslMode;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public void setSchemaName(String schemaName) {
        this.schemaName = schemaName;
    }

    public Integer getDimension() {
        return dimension;
    }

    public void setDimension(Integer dimension) {
        this.dimension = dimension;
    }

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }

    public String getNamespaceColumn() {
        return namespaceColumn;
    }

    public void setNamespaceColumn(String namespaceColumn) {
        this.namespaceColumn = namespaceColumn;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Integer getSearchBeamSize() {
        return searchBeamSize;
    }

    public void setSearchBeamSize(Integer searchBeamSize) {
        this.searchBeamSize = searchBeamSize;
    }

    public String getIndexType() {
        return indexType;
    }

    public void setIndexType(String indexType) {
        this.indexType = indexType;
    }

    public Integer getMinPartitionSize() {
        return minPartitionSize;
    }

    public void setMinPartitionSize(Integer minPartitionSize) {
        this.minPartitionSize = minPartitionSize;
    }

    public Integer getMaxPartitionSize() {
        return maxPartitionSize;
    }

    public void setMaxPartitionSize(Integer maxPartitionSize) {
        this.maxPartitionSize = maxPartitionSize;
    }
}
