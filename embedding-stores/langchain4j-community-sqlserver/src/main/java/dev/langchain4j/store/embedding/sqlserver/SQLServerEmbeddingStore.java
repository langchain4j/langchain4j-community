package dev.langchain4j.store.embedding.sqlserver;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.sqlserver.util.EmbeddingStoreUtil;
import java.sql.*;
import java.util.*;
import java.util.Collections;
import java.util.Properties;
import javax.sql.DataSource;
import microsoft.sql.Vector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SQL Server implementation of {@link EmbeddingStore}.
 * <p>
 * This implementation uses SQL Server database to store embeddings along with their associated text segments and metadata.
 * It leverages SQL Server 2025+'s native VECTOR data type and VECTOR_DISTANCE function for efficient similarity calculations.
 * </p>
 */
public class SQLServerEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger logger = LoggerFactory.getLogger(SQLServerEmbeddingStore.class);

    private final DataSource dataSource;
    private final EmbeddingTable embeddingTable;
    private final DistanceMetric metric;

    private SQLServerEmbeddingStore(
            DataSource dataSource, EmbeddingTable embeddingTable, List<Index> indexes, DistanceMetric metric) {
        this.dataSource = dataSource;
        this.embeddingTable = embeddingTable;
        this.metric = metric == null ? DistanceMetric.COSINE : metric;

        try {
            embeddingTable.create(this.dataSource);

            // Create indexes if configured
            if (indexes != null) {
                for (Index index : indexes) {
                    index.create(dataSource, embeddingTable);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create embedding table or indexes", e);
        }
    }

    /**
     * Creates a builder for configuring a SQLServerEmbeddingStore with a java.sql.DataSource.
     *
     * @return A new builder instance.
     */
    public static SQLServerEmbeddingStoreDataSourceBuilder dataSourceBuilder() {
        return new SQLServerEmbeddingStoreDataSourceBuilder();
    }

    /**
     * Creates a builder for configuring a SQLServerEmbeddingStore with a java.sql.DataSource.
     *
     * @return A new builder instance.
     */
    public static SQLServerEmbeddingStoreConnectionBuilder connectionBuilder() {
        return new SQLServerEmbeddingStoreConnectionBuilder();
    }

    @Override
    public String add(Embedding embedding) {
        List<String> id = addAll(Collections.singletonList(embedding));
        return id.get(0);
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        ensureNotNull(embedding, "embedding");
        ensureNotNull(textSegment, "textSegment");
        List<String> id = addAll(Collections.singletonList(embedding), Collections.singletonList(textSegment));
        return id.get(0);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        ensureNotNull(embeddings, "embeddings");
        String[] ids = new String[embeddings.size()];

        String sql = String.format(
                "INSERT INTO %s (%s, %s) VALUES (?, ?)",
                embeddingTable.getQualifiedTableName(), embeddingTable.idColumn(), embeddingTable.embeddingColumn());

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < embeddings.size(); i++) {
                String id = randomUUID();
                ids[i] = id;

                Embedding embedding = EmbeddingStoreUtil.ensureIndexNotNull(embeddings, i, "embeddings");

                statement.setString(1, id);
                Float[] boxedVector = EmbeddingStoreUtil.boxEmbeddings(embedding.vector());
                Vector vector =
                        new microsoft.sql.Vector(boxedVector.length, Vector.VectorDimensionType.FLOAT32, boxedVector);
                statement.setObject(2, vector, microsoft.sql.Types.VECTOR);

                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException sqlException) {
            logger.error("Failed to add embeddings", sqlException);
            throw new RuntimeException(sqlException);
        }

        return Arrays.asList(ids);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
        return doAddAll(null, embeddings, textSegments);
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        doAddAll(ids, embeddings, textSegments);
    }

    private List<String> doAddAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        ensureNotNull(embeddings, "embeddings");
        ensureNotNull(textSegments, "textSegments");
        if (embeddings.size() != textSegments.size()) {
            throw new IllegalArgumentException(
                    "The list of embeddings and the list of text segments must have the same size");
        }

        final String sql = String.format(
                """
                INSERT INTO %s (%s, %s, %s, %s) VALUES (?, ?, ?, ?)
                """,
                embeddingTable.getQualifiedTableName(),
                embeddingTable.idColumn(),
                embeddingTable.embeddingColumn(),
                embeddingTable.textColumn(),
                embeddingTable.metadataColumn());

        boolean generateIds = false;
        if (ids == null) {
            ids = new ArrayList<>(embeddings.size());
            generateIds = true;
        }
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < embeddings.size(); i++) {
                String id;
                if (generateIds) {
                    id = randomUUID();
                    ids.add(id);
                } else {
                    id = EmbeddingStoreUtil.ensureIndexNotNull(ids, i, "ids");
                }
                statement.setString(1, id);

                Embedding embedding = EmbeddingStoreUtil.ensureIndexNotNull(embeddings, i, "embeddings");
                TextSegment textSegment = EmbeddingStoreUtil.ensureIndexNotNull(textSegments, i, "textSegments");

                Float[] boxedVector = EmbeddingStoreUtil.boxEmbeddings(embedding.vector());
                Vector vector =
                        new microsoft.sql.Vector(boxedVector.length, Vector.VectorDimensionType.FLOAT32, boxedVector);
                statement.setObject(2, vector, microsoft.sql.Types.VECTOR);

                statement.setObject(3, textSegment.text());
                Metadata metadata = textSegment.metadata() != null ? textSegment.metadata() : null;
                statement.setString(4, metadata != null ? EmbeddingStoreUtil.metadataToJson(metadata) : null);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException e) {
            logger.error("Failed to add embeddings", e);
            throw new RuntimeException(e);
        }

        return ids;
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        Embedding referenceEmbedding = request.queryEmbedding();
        int maxResults = request.maxResults();
        double minScore = request.minScore();
        Filter filter = request.filter();

        SQLFilter sqlFilter = SQLFilters.create(filter, embeddingTable::mapMetadataKey);

        // Build query using VECTOR_DISTANCE
        String sql = String.format(
                """
                SELECT TOP (%d)
                 VECTOR_DISTANCE('%s', %s, ?) AS distance,
                 %s
                 FROM %s
                 %s
                 ORDER BY distance ASC
                """,
                maxResults,
                metric.getMetric(),
                embeddingTable.embeddingColumn(),
                String.join(
                        ", ",
                        embeddingTable.idColumn(),
                        embeddingTable.embeddingColumn(),
                        embeddingTable.textColumn(),
                        embeddingTable.metadataColumn()),
                embeddingTable.getQualifiedTableName(),
                sqlFilter.asWhereClause());

        List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            // Set the query embedding vector as the first parameter
            Float[] boxedVector = EmbeddingStoreUtil.boxEmbeddings(referenceEmbedding.vector());
            Vector vector =
                    new microsoft.sql.Vector(boxedVector.length, Vector.VectorDimensionType.FLOAT32, boxedVector);
            statement.setObject(1, vector, microsoft.sql.Types.VECTOR);

            // Set filter parameters starting from index 2
            sqlFilter.setParameters(statement, 2);

            try (ResultSet resultSet = statement.executeQuery()) {
                int resultCount = 0;
                ResultSetMetaData metaData = resultSet.getMetaData();
                int columnCount = metaData.getColumnCount();
                logger.debug("Found {} columns in result set", columnCount);
                while (resultSet.next()) {
                    double distance = resultSet.getDouble("distance");

                    // Convert distance to similarity score using metric-specific conversion
                    double score = metric.distanceToScore(distance) / 2;

                    // Apply minScore filtering
                    if (score >= minScore) {
                        String id = resultSet.getString(embeddingTable.idColumn());

                        Object embeddingVector =
                                resultSet.getObject(embeddingTable.embeddingColumn(), microsoft.sql.Vector.class);
                        Embedding embedding = new Embedding(EmbeddingStoreUtil.unboxEmbeddings(embeddingVector));

                        String text = resultSet.getString(embeddingTable.textColumn());
                        String metadataJson = resultSet.getString(embeddingTable.metadataColumn());

                        TextSegment textSegment = text != null
                                ? TextSegment.from(text, EmbeddingStoreUtil.jsonToMetadata(metadataJson))
                                : null;

                        matches.add(new EmbeddingMatch<>(score, id, embedding, textSegment));
                        resultCount++;
                    } else {
                        logger.debug("Ignoring result with score {} because it is below minScore {}", score, minScore);
                        break;
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to search embeddings", e);
        }

        return new EmbeddingSearchResult<>(matches);
    }

    @Override
    public void removeAll(Filter filter) {

        ensureNotNull(filter, "filter");
        SQLFilter sqlFilter = SQLFilters.create(filter, embeddingTable::mapMetadataKey);

        String sql = "DELETE FROM " + embeddingTable.getQualifiedTableName() + sqlFilter.asWhereClause();

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            // Set filter parameters starting from index 1
            sqlFilter.setParameters(statement, 1);

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to removeAll", e);
        }
    }

    @Override
    public void removeAll(final Collection<String> ids) {
        ensureNotEmpty(ids, "ids");

        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = String.format(
                "DELETE FROM %s WHERE %s IN (%s)",
                embeddingTable.getQualifiedTableName(), embeddingTable.idColumn(), placeholders);

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (String id : ids) {
                statement.setString(index++, id);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void removeAll() {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(String.format("TRUNCATE TABLE %s", embeddingTable.getQualifiedTableName()));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        ensureNotNull(id, "id");
        ensureNotNull(embedding, "embedding");

        String sql = String.format(
                """
                INSERT INTO %s (%s, %s, %s, %s) VALUES (?, ?, ?, ?)
                """,
                embeddingTable.getQualifiedTableName(),
                embeddingTable.idColumn(),
                embeddingTable.embeddingColumn(),
                embeddingTable.textColumn(),
                embeddingTable.metadataColumn());

        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            Float[] boxedVector = EmbeddingStoreUtil.boxEmbeddings(embedding.vector());
            Vector vector =
                    new microsoft.sql.Vector(boxedVector.length, Vector.VectorDimensionType.FLOAT32, boxedVector);
            statement.setString(1, id);
            statement.setObject(2, vector, microsoft.sql.Types.VECTOR);
            statement.setString(3, textSegment != null ? textSegment.text() : null);

            Metadata metadata = textSegment != null ? textSegment.metadata() : null;
            if (metadata == null) {
                statement.setNull(4, Types.VARCHAR);
            } else {
                statement.setString(4, EmbeddingStoreUtil.metadataToJson(metadata));
            }

            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to add embedding", e);
        }
    }

    /**
     * Builder class for creating SQLServerEmbeddingStore instances.
     */
    public abstract static class Builder {
        /**
         * Represents the table used for storing embeddings in the SQL server.
         * Configured within the builder to facilitate the creation of SQLServerEmbeddingStore instances.
         */
        protected EmbeddingTable embeddingTable;
        /**
         * Represents a collection of database indexes configured for the associated
         * embedding table. These indexes are used to optimize database queries,
         * particularly for the metadata column in cases such as JSON indexing.
         *
         * The indexes can be built and added to the builder dynamically during the
         * process of creating an SQLServerEmbeddingStore instance.
         */
        protected List<Index> indexes;
        /**
         * Defines the distance metric to be used for similarity calculations between embeddings.
         * The available options include COSINE and EUCLIDEAN, each offering a different method
         * for determining proximity or relevance based on embeddings' vector representations.
         *
         * This variable is configurable within the builder class to customize the behavior
         * of embedding store objects for specific use cases.
         */
        protected DistanceMetric metric;

        /**
         * Sets the embedding table to be used for storing embeddings in the SQL server.
         *
         * @param embeddingTable the {@code EmbeddingTable} object representing the table for storing embeddings
         * @return the {@code Builder} instance to allow for method chaining
         */
        public Builder embeddingTable(EmbeddingTable embeddingTable) {
            this.embeddingTable = embeddingTable;
            return this;
        }

        /**
         * Sets the list of database indexes to be associated with the embedding table.
         * These indexes are used to optimize database queries, particularly for metadata columns.
         *
         * @param indexes the list of {@code Index} objects representing the database indexes to be configured
         * @return the {@code Builder} instance to allow for method chaining
         */
        public Builder indexes(List<Index> indexes) {
            this.indexes = indexes;
            return this;
        }

        /**
         * Adds a single {@link Index} to the list of indexes associated with the embedding table.
         * If the list of indexes is null, it initializes the list before adding the index.
         *
         * @param index the {@link Index} object to be added
         * @return the {@code Builder} instance to allow for method chaining
         */
        public Builder addIndex(Index index) {
            if (this.indexes == null) this.indexes = new ArrayList<>();
            this.indexes.add(index);
            return this;
        }

        /**
         * Sets the distance metric to be used for calculations. The distance metric
         * determines how similarity or relevance is calculated between stored and
         * queried embeddings.
         *
         * @param metric the {@link DistanceMetric} to be used, such as {@code COSINE} or {@code EUCLIDEAN}
         * @return the {@code Builder} instance to allow for method chaining
         */
        public Builder metric(DistanceMetric metric) {
            this.metric = metric;
            return this;
        }

        /**
         * Builds and returns an instance of {@code SQLServerEmbeddingStore} based on the
         * properties configured in the {@code Builder}.
         *
         * @return a new instance of {@code SQLServerEmbeddingStore} configured with the
         *         specified embedding table, indexes, and distance metric.
         */
        public abstract SQLServerEmbeddingStore build();
    }

    /**
     * Builder class for creating SQLServerEmbeddingStore instances.
     */
    public static class SQLServerEmbeddingStoreDataSourceBuilder extends Builder {
        private DataSource dataSource;

        private SQLServerEmbeddingStoreDataSourceBuilder() {}

        /**
         * Sets the data source.
         *
         * @param dataSource The data source.
         * @return This builder.
         */
        public Builder dataSource(DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        /**
         * Builds the SQLServerEmbeddingStore instance.
         *
         * @return The configured SQLServerEmbeddingStore.
         */
        @Override
        public SQLServerEmbeddingStore build() {
            ensureNotNull(dataSource, "dataSource");
            ensureNotNull(embeddingTable, "embeddingTable");
            return new SQLServerEmbeddingStore(dataSource, embeddingTable, indexes, metric);
        }
    }

    /**
     * Builder for configuring and creating instances of SQLServerEmbeddingStore.
     * This class allows setting various parameters necessary for establishing a connection
     * to a Microsoft SQL Server database and creating an embedding store.
     */
    public static class SQLServerEmbeddingStoreConnectionBuilder extends Builder {
        private String host;
        private int port = -1;
        private String database;
        private String username;
        private String password;
        private Properties connectionProperties;

        /**
         * Sets the SQL Server host name or IP address.
         *
         * @param host The host name or IP address of the SQL Server instance
         * @return This builder
         */
        public SQLServerEmbeddingStoreConnectionBuilder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * Sets the SQL Server port number.
         *
         * @param port The port number (default is 1433 if not specified)
         * @return This builder
         */
        public SQLServerEmbeddingStoreConnectionBuilder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Sets the database name to connect to.
         *
         * @param database The name of the database
         * @return This builder
         */
        public SQLServerEmbeddingStoreConnectionBuilder database(String database) {
            this.database = database;
            return this;
        }

        /**
         * Sets the username for database authentication.
         *
         * @param username The username for connecting to the database
         * @return This builder
         */
        public SQLServerEmbeddingStoreConnectionBuilder userName(String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets the password for database authentication.
         *
         * @param password The password for connecting to the database
         * @return This builder
         */
        public SQLServerEmbeddingStoreConnectionBuilder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Sets additional connection properties for the SQL Server DataSource.
         *
         * @param connectionProperties A Properties object containing additional connection settings
         * @return This builder
         */
        public SQLServerEmbeddingStoreConnectionBuilder connectionProperties(Properties connectionProperties) {
            this.connectionProperties = connectionProperties;
            return this;
        }

        /**
         * Sets a single connection property.
         *
         * @param key Property name
         * @param value Property value
         * @return This builder
         */
        public SQLServerEmbeddingStoreConnectionBuilder connectionProperty(String key, Object value) {
            if (this.connectionProperties == null) {
                this.connectionProperties = new Properties();
            }
            this.connectionProperties.put(key, value);
            return this;
        }

        @Override
        public SQLServerEmbeddingStore build() {
            ensureNotNull(embeddingTable, "embeddingTable");
            ensureNotNull(host, "host");

            SQLServerDataSource ds = new SQLServerDataSource();
            ds.setServerName(host);
            if (port != -1) {
                ds.setPortNumber(port);
            }
            if (database != null) {
                ds.setDatabaseName(database);
            }
            if (username != null) {
                ds.setUser(username);
            }
            if (password != null) {
                ds.setPassword(password);
            }

            // Apply additional connection properties
            if (connectionProperties != null) {
                applyConnectionProperties(ds, connectionProperties);
            }

            return new SQLServerEmbeddingStore(ds, embeddingTable, indexes, metric);
        }

        /**
         * Applies connection properties to the SQLServerDataSource using reflection.
         * This allows setting any property supported by SQLServerDataSource.
         *
         * @param dataSource The SQLServerDataSource to configure
         * @param properties The properties to apply
         */
        private void applyConnectionProperties(SQLServerDataSource dataSource, Properties properties) {
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                try {
                    // Try to find a setter method for this property
                    String setterName = "set" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
                    java.lang.reflect.Method setter = findSetterMethod(dataSource.getClass(), setterName, value);
                    if (setter != null) {
                        Object convertedValue = convertValue(setter.getParameterTypes()[0], value);
                        setter.invoke(dataSource, convertedValue);
                        logger.debug("Applied connection property: {} = {}", key, value);
                    } else {
                        logger.warn("Could not find setter method for property: {}", key);
                    }
                } catch (Exception e) {
                    logger.warn("Failed to set connection property: {} = {}", key, value, e);
                }
            }
        }

        /**
         * Finds a setter method that can accept the given value.
         */
        private java.lang.reflect.Method findSetterMethod(Class<?> clazz, String methodName, String value) {
            java.lang.reflect.Method[] methods = clazz.getMethods();
            for (java.lang.reflect.Method method : methods) {
                if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
                    return method;
                }
            }
            return null;
        }

        /**
         * Converts a string value to the appropriate type for the setter parameter.
         */
        private Object convertValue(Class<?> targetType, String value) {
            if (targetType == String.class) {
                return value;
            } else if (targetType == boolean.class || targetType == Boolean.class) {
                return Boolean.parseBoolean(value);
            } else if (targetType == int.class || targetType == Integer.class) {
                return Integer.parseInt(value);
            } else if (targetType == long.class || targetType == Long.class) {
                return Long.parseLong(value);
            } else {
                // Default to string representation
                return value;
            }
        }
    }
}
