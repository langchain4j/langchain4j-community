package dev.langchain4j.community.store.embedding.yugabytedb;

import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.randomUUID;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import com.pgvector.PGvector;
import dev.langchain4j.community.store.embedding.yugabytedb.index.BaseIndex;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * YugabyteDB EmbeddingStore Implementation
 * YugabyteDB is a distributed SQL database that supports PostgreSQL-compatible
 * APIs.
 * This implementation leverages the pgvector extension for vector similarity
 * search.
 * Instances of this store are created by configuring a builder:
 * <pre>{@code
 * EmbeddingStore<TextSegment> store = YugabyteDBEmbeddingStore.builder()
 *     .engine(yugabyteDBEngine)
 *     .schema(schema)
 *     .build();
 * }</pre>
 */
public class YugabyteDBEmbeddingStore implements EmbeddingStore<TextSegment> {

    private final YugabyteDBEngine engine;
    private final YugabyteDBSchema schema;
    private final MetadataHandler metadataHandler;

    /**
     * Constructor for YugabyteDBEmbeddingStore
     *
     * @param builder builder.
     */
    public YugabyteDBEmbeddingStore(Builder builder) {
        this.engine = builder.engine;
        this.schema = builder.schema;
        this.metadataHandler = MetadataHandlerFactory.create(builder.metadataStorageConfig);

        if (schema.isCreateTableIfNotExists()) {
            createTableIfNotExists();
        }
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        add(id, embedding, textSegment);
        return id;
    }

    public void add(String id, Embedding embedding, TextSegment textSegment) {
        addInternal(id, embedding, textSegment);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream().map(embedding -> randomUUID()).collect(toList());

        addAllInternal(ids, embeddings, null);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
        if (embeddings.size() != textSegments.size()) {
            throw new IllegalArgumentException("The list of embeddings and text segments must have the same size");
        }

        List<String> ids = embeddings.stream().map(embedding -> randomUUID()).collect(toList());

        addAllInternal(ids, embeddings, textSegments);
        return ids;
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        if (ids.size() != embeddings.size() || embeddings.size() != textSegments.size()) {
            throw new IllegalArgumentException("The list of ids, embeddings and text segments must have the same size");
        }

        addAllInternal(ids, embeddings, textSegments);
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        Embedding referenceEmbedding = request.queryEmbedding();
        int maxResults = request.maxResults();
        double minScore = request.minScore();
        Filter filter = request.filter();

        // Create dynamic search SQL that works with metadata handler
        String sql = createSearchSql();

        if (filter != null) {
            String whereClause = metadataHandler.whereClause(filter);
            sql += " WHERE " + whereClause;
        }

        sql += String.format(" ORDER BY %s %s ? LIMIT ?", schema.getEmbeddingColumn(), schema.getDistanceFunction());

        try (Connection connection = engine.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            // Register PGvector types for this connection before using prepared statements
            registerPGVectorTypes(connection);

            List<Float> vectorList = referenceEmbedding.vectorAsList();
            float[] vectorArray = new float[vectorList.size()];
            for (int i = 0; i < vectorList.size(); i++) {
                vectorArray[i] = vectorList.get(i);
            }
            PGvector queryVector = new PGvector(vectorArray);
            int paramIndex = 1;
            setPGVectorParameter(statement, paramIndex++, queryVector);

            if (filter != null) {
                paramIndex = metadataHandler.setFilterParameters(statement, filter, paramIndex);
                setPGVectorParameter(statement, paramIndex++, queryVector);
                statement.setInt(paramIndex, maxResults);
            } else {
                setPGVectorParameter(statement, paramIndex++, queryVector);
                statement.setInt(paramIndex, maxResults);
            }

            try (ResultSet resultSet = statement.executeQuery()) {
                List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();

                while (resultSet.next()) {
                    String id = resultSet.getString(schema.getIdColumn());
                    String content = resultSet.getString(schema.getContentColumn());
                    double distance = resultSet.getDouble("distance");

                    double score = calculateScore(distance);
                    if (score < minScore) {
                        continue;
                    }

                    Embedding embedding = extractEmbeddingFromResultSet(resultSet, schema.getEmbeddingColumn());

                    Metadata metadata = metadataHandler.fromResultSet(resultSet);
                    TextSegment textSegment = isNotNullOrBlank(content) ? TextSegment.from(content, metadata) : null;

                    matches.add(new EmbeddingMatch<>(score, id, embedding, textSegment));
                }

                return new EmbeddingSearchResult<>(matches);
            }
        } catch (SQLException e) {
            throw new YugabyteDBRequestFailedException("Failed to search embeddings", e);
        } catch (YugabyteDBRequestFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new YugabyteDBRequestFailedException("Failed to search embeddings", e);
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids cannot be null or empty");
        }

        try (Connection connection = engine.getConnection();
                PreparedStatement statement = connection.prepareStatement(schema.getDeleteSql())) {

            for (String id : ids) {
                statement.setString(1, id);
                statement.addBatch();
            }

            statement.executeBatch();
        } catch (SQLException e) {
            throw new YugabyteDBRequestFailedException("Failed to remove embeddings", e);
        } catch (YugabyteDBRequestFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new YugabyteDBRequestFailedException("Failed to remove embeddings", e);
        }
    }

    @Override
    public void removeAll(Filter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("filter cannot be null");
        }

        String whereClause = metadataHandler.whereClause(filter);
        String sql = String.format("DELETE FROM %s WHERE %s", schema.getFullTableName(), whereClause);

        try (Connection connection = engine.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            metadataHandler.setFilterParameters(statement, filter, 1);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new YugabyteDBRequestFailedException("Failed to remove embeddings by filter", e);
        } catch (YugabyteDBRequestFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new YugabyteDBRequestFailedException("Failed to remove embeddings by filter", e);
        }
    }

    @Override
    public void removeAll() {
        try (Connection connection = engine.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(schema.getDeleteAllSql());
        } catch (SQLException e) {
            throw new YugabyteDBRequestFailedException("Failed to remove all embeddings", e);
        } catch (YugabyteDBRequestFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new YugabyteDBRequestFailedException("Failed to remove all embeddings", e);
        }
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAllInternal(
                singletonList(id), singletonList(embedding), textSegment != null ? singletonList(textSegment) : null);
    }

    private void addAllInternal(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        // Create dynamic INSERT SQL with metadata handler
        String insertSql = createInsertSql();

        try (Connection connection = engine.getConnection();
                PreparedStatement statement = connection.prepareStatement(insertSql)) {

            // Register PGvector types for this connection before using prepared statements
            registerPGVectorTypes(connection);

            for (int i = 0; i < embeddings.size(); i++) {
                String id = ids.get(i);
                Embedding embedding = embeddings.get(i);
                TextSegment textSegment = textSegments != null ? textSegments.get(i) : null;

                int paramIndex = 1;
                statement.setObject(paramIndex++, java.util.UUID.fromString(id));
                statement.setString(paramIndex++, textSegment != null ? textSegment.text() : null);

                // Use metadata handler to set metadata parameters
                Metadata metadata = textSegment != null ? textSegment.metadata() : new Metadata();
                paramIndex = metadataHandler.setMetadata(statement, paramIndex, metadata);

                // Set embedding vector
                List<Float> embeddingList = embedding.vectorAsList();
                float[] embeddingArray = new float[embeddingList.size()];
                for (int j = 0; j < embeddingList.size(); j++) {
                    embeddingArray[j] = embeddingList.get(j);
                }
                PGvector vector = new PGvector(embeddingArray);
                setPGVectorParameter(statement, paramIndex, vector);

                statement.addBatch();
            }

            statement.executeBatch();
        } catch (SQLException e) {
            throw new YugabyteDBRequestFailedException("Failed to add embeddings", e);
        } catch (YugabyteDBRequestFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new YugabyteDBRequestFailedException("Failed to add embeddings", e);
        }
    }

    /**
     * Safely registers PGvector types for both PostgreSQL and YugabyteDB Smart Driver connections
     */
    private void registerPGVectorTypes(Connection connection) {
        try {
            // Try to register PGvector types - this works for PostgreSQL JDBC driver
            PGvector.registerTypes(connection);
        } catch (SQLException e) {
            // Handle YugabyteDB Smart Driver connections that cannot unwrap to PostgreSQL connections
            if (e.getMessage() != null
                    && e.getMessage().toLowerCase().contains("cannot unwrap to org.postgresql.pgconnection")) {
                // Smart Driver doesn't need explicit PGvector type registration - it handles vector types natively
                // Silently skip registration for Smart Driver
            } else {
                // Re-throw other SQL exceptions
                throw new YugabyteDBRequestFailedException("Failed to register PGvector types", e);
            }
        }
    }

    /**
     * Safely sets PGvector parameters for both PostgreSQL and YugabyteDB Smart Driver connections
     */
    private void setPGVectorParameter(PreparedStatement statement, int paramIndex, PGvector vector)
            throws SQLException {
        try {
            // Try standard setObject first - works for PostgreSQL JDBC driver
            statement.setObject(paramIndex, vector);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("Can't infer the SQL type")) {
                // For YugabyteDB Smart Driver, explicitly specify the SQL type
                // Use Types.OTHER which is the standard type for custom PostgreSQL types
                statement.setObject(paramIndex, vector, java.sql.Types.OTHER);
            } else {
                // Re-throw other SQL exceptions
                throw e;
            }
        }
    }

    /**
     * Safely extracts embedding vectors from ResultSet for both PostgreSQL and YugabyteDB Smart Driver connections
     */
    private Embedding extractEmbeddingFromResultSet(ResultSet resultSet, String columnName) throws SQLException {
        Object vectorObject = resultSet.getObject(columnName);

        if (vectorObject instanceof PGvector) {
            // PostgreSQL JDBC driver returns PGvector objects
            PGvector pgvector = (PGvector) vectorObject;
            return Embedding.from(pgvector.toArray());
        } else if (vectorObject != null && vectorObject.getClass().getName().equals("com.yugabyte.util.PGobject")) {
            // YugabyteDB Smart Driver returns PGobject - extract the vector string and parse it
            String vectorString = vectorObject.toString();
            return parseVectorString(vectorString);
        } else {
            throw new YugabyteDBRequestFailedException("Unsupported vector object type: "
                    + (vectorObject != null ? vectorObject.getClass().getName() : "null"));
        }
    }

    /**
     * Parses a vector string in PostgreSQL array format (e.g., "[1.0,2.0,3.0]") to an Embedding
     */
    private Embedding parseVectorString(String vectorString) {
        try {
            // Remove brackets and split by comma
            String cleanString = vectorString.trim();
            if (cleanString.startsWith("[") && cleanString.endsWith("]")) {
                cleanString = cleanString.substring(1, cleanString.length() - 1);
            }

            String[] parts = cleanString.split(",");
            float[] vector = new float[parts.length];

            for (int i = 0; i < parts.length; i++) {
                vector[i] = Float.parseFloat(parts[i].trim());
            }

            return Embedding.from(vector);
        } catch (Exception e) {
            throw new YugabyteDBRequestFailedException("Failed to parse vector string: " + vectorString, e);
        }
    }

    /**
     * Creates dynamic INSERT SQL that works with the metadata handler
     */
    private String createInsertSql() {
        List<String> metadataColumns = metadataHandler.columnsNames();

        // Build column names
        StringBuilder columns = new StringBuilder();
        columns.append(schema.getIdColumn()).append(", ");
        columns.append(schema.getContentColumn()).append(", ");
        for (String col : metadataColumns) {
            columns.append(col).append(", ");
        }
        columns.append(schema.getEmbeddingColumn());

        // Build placeholders
        StringBuilder placeholders = new StringBuilder();
        placeholders.append("?::uuid, ?"); // id and content
        for (int i = 0; i < metadataColumns.size(); i++) {
            placeholders.append(", ?");
        }
        placeholders.append(", ?::vector"); // embedding

        // Build update clause
        String updateClause = schema.getContentColumn() + " = EXCLUDED."
                + schema.getContentColumn()
                + ", "
                + metadataHandler.insertClause()
                + ", " + schema.getEmbeddingColumn()
                + " = EXCLUDED." + schema.getEmbeddingColumn();

        return String.format(
                "INSERT INTO %s (%s) VALUES (%s) " + "ON CONFLICT (%s) DO UPDATE SET %s",
                schema.getFullTableName(),
                columns.toString(),
                placeholders.toString(),
                schema.getIdColumn(),
                updateClause);
    }

    /**
     * Creates dynamic search SQL that works with the metadata handler
     */
    private String createSearchSql() {
        List<String> metadataColumns = metadataHandler.columnsNames();

        StringBuilder columns = new StringBuilder();
        columns.append(schema.getIdColumn()).append(", ");
        columns.append(schema.getContentColumn()).append(", ");
        for (String col : metadataColumns) {
            columns.append(col).append(", ");
        }
        columns.append(schema.getEmbeddingColumn()).append(", ");
        columns.append(schema.getEmbeddingColumn())
                .append(" ")
                .append(schema.getDistanceFunction())
                .append(" ? AS distance");

        return String.format("SELECT %s FROM %s", columns.toString(), schema.getFullTableName());
    }

    public void createTableIfNotExists() {
        try (Connection connection = engine.getConnection();
                Statement statement = connection.createStatement()) {

            // Ensure pgvector extension is enabled
            statement.execute("CREATE EXTENSION IF NOT EXISTS vector");

            String metadataColumns = metadataHandler.columnDefinitionsString();
            if (isNotNullOrBlank(metadataColumns)) {
                metadataColumns += ", ";
            }

            // Create table with metadata handler column definitions
            String createTableSql = String.format(
                    "CREATE TABLE IF NOT EXISTS %s (" + "%s UUID PRIMARY KEY, "
                            + "%s TEXT, "
                            + "%s"
                            + "%s vector(%d)"
                            + ")",
                    schema.getFullTableName(),
                    schema.getIdColumn(),
                    schema.getContentColumn(),
                    metadataColumns,
                    schema.getEmbeddingColumn(),
                    schema.getDimension());
            statement.execute(createTableSql);

            // Create metadata indexes
            metadataHandler.createMetadataIndexes(statement, schema.getFullTableName());

            // Create vector index only if index SQL is provided (not null)
            String indexSql = schema.getCreateIndexSql();
            if (indexSql != null) {
                statement.execute(indexSql);
            }

        } catch (SQLException e) {
            throw new YugabyteDBRequestFailedException("Failed to create table", e);
        } catch (YugabyteDBRequestFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new YugabyteDBRequestFailedException("Failed to create table", e);
        }
    }

    private double calculateScore(double distance) {
        return switch (schema.getMetricType()) {
            case COSINE -> RelevanceScore.fromCosineSimilarity(1.0 - distance);
            case EUCLIDEAN -> RelevanceScore.fromCosineSimilarity(1.0 / (1.0 + distance));
            case DOT_PRODUCT ->
                // DOT_PRODUCT returns negative distances, convert to positive similarity score
                // Higher dot product (less negative) = higher similarity
                Math.abs(distance) + 1.0; // Convert -1 to 2.0, -0.5 to 1.5, 0 to 1.0
            default -> RelevanceScore.fromCosineSimilarity(1.0 - distance);
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private YugabyteDBEngine engine;
        private YugabyteDBSchema schema;
        private MetadataStorageConfig metadataStorageConfig;

        public Builder engine(YugabyteDBEngine engine) {
            this.engine = engine;
            return this;
        }

        public Builder schema(YugabyteDBSchema schema) {
            this.schema = schema;
            return this;
        }

        public Builder metadataStorageConfig(MetadataStorageConfig metadataStorageConfig) {
            this.metadataStorageConfig = metadataStorageConfig;
            return this;
        }

        // Convenience methods for backward compatibility
        public Builder tableName(String tableName) {
            ensureSchemaBuilder().tableName(tableName);
            return this;
        }

        public Builder schemaName(String schemaName) {
            ensureSchemaBuilder().schemaName(schemaName);
            return this;
        }

        public Builder contentColumn(String contentColumn) {
            ensureSchemaBuilder().contentColumn(contentColumn);
            return this;
        }

        public Builder embeddingColumn(String embeddingColumn) {
            ensureSchemaBuilder().embeddingColumn(embeddingColumn);
            return this;
        }

        public Builder idColumn(String idColumn) {
            ensureSchemaBuilder().idColumn(idColumn);
            return this;
        }

        public Builder metadataColumn(String metadataColumn) {
            ensureSchemaBuilder().metadataColumn(metadataColumn);
            return this;
        }

        public Builder metricType(MetricType metricType) {
            ensureSchemaBuilder().metricType(metricType);
            return this;
        }

        public Builder dimension(Integer dimension) {
            ensureSchemaBuilder().dimension(dimension);
            return this;
        }

        /**
         * Configure the vector index type for similarity search optimization.
         *
         * <p>Available index types for YugabyteDB:</p>
         * <ul>
         *   <li><b>HNSW</b> - Hierarchical Navigable Small World (ybhnsw): Fast approximate search with high recall.
         *       Best for most use cases. YugabyteDB's native vector index implementation.</li>
         *   <li><b>NoIndex</b> - Sequential scan: Exact search but slower.
         *       Use for small datasets or when exact results are required.</li>
         * </ul>
         *
         * <p><b>Note:</b> YugabyteDB uses <code>ybhnsw</code> as its HNSW implementation.
         * IVFFlat is not supported by YugabyteDB.</p>
         *
         * <p>Example usage:</p>
         * <pre>
         * // HNSW index (recommended for YugabyteDB)
         * .vectorIndex(HNSWIndex.builder()
         *     .m(16)
         *     .efConstruction(64)
         *     .metricType(MetricType.COSINE)
         *     .build())
         *
         * // No index for exact search
         * .vectorIndex(new NoIndex())
         * </pre>
         *
         * @param vectorIndex the vector index configuration
         * @return this builder
         */
        public Builder vectorIndex(BaseIndex vectorIndex) {
            ensureSchemaBuilder().vectorIndex(vectorIndex);
            return this;
        }

        public Builder createTableIfNotExists(boolean createTableIfNotExists) {
            ensureSchemaBuilder().createTableIfNotExists(createTableIfNotExists);
            return this;
        }

        private YugabyteDBSchema.Builder schemaBuilder;

        private YugabyteDBSchema.Builder ensureSchemaBuilder() {
            if (schemaBuilder == null) {
                schemaBuilder = YugabyteDBSchema.builder();
            }
            return schemaBuilder;
        }

        public YugabyteDBEmbeddingStore build() {
            if (engine == null) {
                throw new IllegalArgumentException("YugabyteDBEngine is required");
            }

            if (schema == null) {
                if (schemaBuilder == null) {
                    throw new IllegalArgumentException("Either schema or dimension must be specified");
                }
                schema = schemaBuilder.build();
            }

            return new YugabyteDBEmbeddingStore(this);
        }
    }
}
