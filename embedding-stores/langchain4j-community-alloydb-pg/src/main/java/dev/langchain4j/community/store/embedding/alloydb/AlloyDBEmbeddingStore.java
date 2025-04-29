package dev.langchain4j.community.store.embedding.alloydb;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.Utils.isNullOrBlank;
import static dev.langchain4j.internal.Utils.randomUUID;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import dev.langchain4j.community.store.embedding.alloydb.filter.AlloyDBFilterMapper;
import dev.langchain4j.community.store.embedding.alloydb.index.BaseIndex;
import dev.langchain4j.community.store.embedding.alloydb.index.DistanceStrategy;
import dev.langchain4j.community.store.embedding.alloydb.index.ScaNNIndex;
import dev.langchain4j.community.store.embedding.alloydb.index.query.QueryOptions;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AlloyDB EmbeddingStore Implementation
 * <p>
 * Instances of this store are created by configuring a builder:
 * </p>{@code
 * EmbeddingStore<TextSegment> store = new AlloyDBEmbeddingStore.Builder(alloyDBEngine, "TABLE_NAME")
 * .metadataColumns(metadataColumnNames)
 * .build();}
 */
public class AlloyDBEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);
    private final AlloyDBFilterMapper FILTER_MAPPER = new AlloyDBFilterMapper();
    private final AlloyDBEngine engine;
    private final String tableName;
    private final String schemaName;
    private final String contentColumn;
    private final String embeddingColumn;
    private final String idColumn;
    private final List<String> metadataColumns;
    private final DistanceStrategy distanceStrategy;
    private final QueryOptions queryOptions;
    private String metadataJsonColumn;
    private final String insertQuery;
    private final String deleteQuery;

    /**
     * Constructor for AlloyDBEmbeddingStore
     *
     * @param builder builder.
     */
    public AlloyDBEmbeddingStore(Builder builder) {
        this.engine = builder.engine;
        this.tableName = builder.tableName;
        this.schemaName = builder.schemaName;
        this.contentColumn = builder.contentColumn;
        this.embeddingColumn = builder.embeddingColumn;
        this.idColumn = builder.idColumn;
        this.metadataJsonColumn = builder.metadataJsonColumn;
        this.metadataColumns = builder.metadataColumns;
        this.distanceStrategy = builder.distanceStrategy;
        this.queryOptions = builder.queryOptions;

        // check columns exist in the table
        verifyEmbeddingStoreColumns(builder.ignoreMetadataColumnNames);
        insertQuery = generateInsertQuery();
        deleteQuery = String.format("DELETE FROM \"%s\".\"%s\" WHERE %s = ANY(?)", schemaName, tableName, idColumn);
    }

    private void verifyEmbeddingStoreColumns(List<String> ignoredColumns) {
        if (!metadataColumns.isEmpty() && !ignoredColumns.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot use both metadataColumns and ignoreMetadataColumns at the same time.");
        }

        String query = String.format(
                "SELECT column_name, data_type FROM information_schema.columns WHERE table_name = '%s' AND table_schema = '%s'",
                tableName, schemaName);

        Map<String, String> allColumns = new HashMap<>();

        try (Connection conn = engine.getConnection()) {

            ResultSet resultSet = conn.createStatement().executeQuery(query);

            while (resultSet.next()) {
                allColumns.put(resultSet.getString("column_name"), resultSet.getString("data_type"));
            }

            if (!allColumns.containsKey(idColumn)) {
                throw new IllegalStateException("Id column, " + idColumn + ", does not exist.");
            }
            if (!allColumns.containsKey(contentColumn)) {
                throw new IllegalStateException("Content column, " + contentColumn + ", does not exist.");
            }
            if (!allColumns.get(contentColumn).equalsIgnoreCase("text")
                    && !allColumns.get(contentColumn).contains("char")) {
                throw new IllegalStateException("Content column, is type " + allColumns.get(contentColumn)
                        + ". It must be a type of character string.");
            }
            if (!allColumns.containsKey(embeddingColumn)) {
                throw new IllegalStateException("Embedding column, " + embeddingColumn + ", does not exist.");
            }
            if (!allColumns.get(embeddingColumn).equalsIgnoreCase("USER-DEFINED")) {
                throw new IllegalStateException("Embedding column, " + embeddingColumn + ", is not type Vector.");
            }
            if (!allColumns.containsKey(metadataJsonColumn)) {
                metadataJsonColumn = null;
            }

            for (String metadataColumn : metadataColumns) {
                if (!allColumns.containsKey(metadataColumn)) {
                    throw new IllegalStateException("Metadata column, " + metadataColumn + ", does not exist.");
                }
            }

            if (ignoredColumns != null && !ignoredColumns.isEmpty()) {

                Map<String, String> allColumnsCopy =
                        allColumns.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
                ignoredColumns.add(idColumn);
                ignoredColumns.add(contentColumn);
                ignoredColumns.add(embeddingColumn);

                for (String ignore : ignoredColumns) {
                    allColumnsCopy.remove(ignore);
                }

                metadataColumns.addAll(allColumnsCopy.keySet());
            }

        } catch (SQLException ex) {
            throw new RuntimeException(
                    "Exception caught when verifying vector store table: \"" + schemaName + "\".\"" + tableName + "\"",
                    ex);
        }
    }

    private String generateInsertQuery() {
        String metadataColumnNames =
                metadataColumns.stream().map(column -> "\"" + column + "\"").collect(Collectors.joining(", "));

        // idColumn, contentColumn and embeddedColumn
        int totalColumns = 3;

        if (isNotNullOrEmpty(metadataColumnNames)) {
            totalColumns += metadataColumnNames.split(",").length;
            metadataColumnNames = ", " + metadataColumnNames;
        }

        if (isNotNullOrEmpty(metadataJsonColumn)) {
            metadataColumnNames += ", \"" + metadataJsonColumn + "\"";
            totalColumns++;
        }

        String placeholders = "?";
        for (int p = 1; p < totalColumns; p++) {
            placeholders += ", ?";
        }

        return String.format(
                "INSERT INTO \"%s\".\"%s\" (\"%s\", \"%s\", \"%s\"%s) VALUES (%s)",
                schemaName, tableName, idColumn, embeddingColumn, contentColumn, metadataColumnNames, placeholders);
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        addInternal(id, embedding, null);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        List<TextSegment> emptyTextSegments = Collections.nCopies(ids.size(), null);
        addAll(ids, embeddings, emptyTextSegments);
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegment) {
        List<String> ids = embeddings.stream().map(ignored -> randomUUID()).collect(toList());
        addAll(ids, embeddings, textSegment);
        return ids;
    }

    /**
     * Searches for the most similar (closest in the embedding space) {@link Embedding}s.
     * <br>
     * All search criteria are defined inside the {@link EmbeddingSearchRequest}.
     * <br>
     * {@link EmbeddingSearchRequest#filter()} can be used to filter by various metadata entries
     * based on MetadataColumns in the EmbeddingStoreConfig.
     *
     * @param request A request to search in an {@link EmbeddingStore}. Contains all search criteria.
     * @return An {@link EmbeddingSearchResult} containing all found {@link Embedding}s.
     * Included {@link EmbeddingMatch} scores are derived from chosen {@link DistanceStrategy} set in
     * the {@link AlloyDBEmbeddingStore}.
     */
    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        List<String> columns = new ArrayList<>(metadataColumns);
        columns.add(idColumn);
        columns.add(contentColumn);
        columns.add(embeddingColumn);
        if (isNotNullOrBlank(metadataJsonColumn)) {
            columns.add(metadataJsonColumn);
        }
        String columnNames =
                columns.stream().map(c -> String.format("\"%s\"", c)).collect(Collectors.joining(", "));

        String filterString = FILTER_MAPPER.map(request.filter());

        String whereClause = isNotNullOrBlank(filterString) ? String.format("WHERE %s", filterString) : "";

        String selectQuery = String.format(
                "SELECT %s, %s(%s, ?) as distance FROM \"%s\".\"%s\" %s ORDER BY %s %s ? LIMIT ?;",
                columnNames,
                distanceStrategy.getSearchFunction(),
                embeddingColumn,
                schemaName,
                tableName,
                whereClause,
                embeddingColumn,
                distanceStrategy.getOperator());

        List<EmbeddingMatch<TextSegment>> embeddingMatches = new ArrayList<>();

        try (Connection conn = engine.getConnection()) {
            PGvector.registerTypes(conn);
            try (Statement statement = conn.createStatement()) {
                if (queryOptions != null) {
                    for (String option : queryOptions.getParameterSettings()) {
                        statement.executeQuery(String.format("SET LOCAL %s;", option));
                    }
                }
            }
            try (PreparedStatement preparedStatement = conn.prepareStatement(selectQuery)) {
                preparedStatement.setObject(
                        1, new PGvector(request.queryEmbedding().vector()));
                preparedStatement.setObject(
                        2, new PGvector(request.queryEmbedding().vector()));
                preparedStatement.setInt(3, request.maxResults());
                ResultSet resultSet = preparedStatement.executeQuery();
                while (resultSet.next()) {
                    double score = calculateRelevanceScore(resultSet.getDouble("distance"));

                    if (score < request.minScore()) {
                        continue;
                    }

                    String embeddingId = resultSet.getString(idColumn);

                    PGvector pgVector = (PGvector) resultSet.getObject(embeddingColumn);

                    Embedding embedding = Embedding.from(pgVector.toArray());

                    String embeddedText = resultSet.getString(contentColumn);

                    Map<String, Object> metadataMap = new HashMap<>();

                    for (String metadataColumn : metadataColumns) {
                        if (resultSet.getObject(metadataColumn) != null) {
                            metadataMap.put(metadataColumn, resultSet.getObject(metadataColumn));
                        }
                    }

                    if (isNotNullOrBlank(metadataJsonColumn)) {
                        String metadataJsonString = getOrDefault(resultSet.getString(metadataJsonColumn), "{}");
                        Map<String, Object> metadataJsonMap = OBJECT_MAPPER.readValue(metadataJsonString, Map.class);
                        metadataMap.putAll(metadataJsonMap);
                    }

                    Metadata metadata = Metadata.from(metadataMap);

                    TextSegment embedded = embeddedText != null ? new TextSegment(embeddedText, metadata) : null;

                    embeddingMatches.add(new EmbeddingMatch<>(score, embeddingId, embedding, embedded));
                }
            } catch (JsonProcessingException ex) {
                throw new RuntimeException("Exception caught when processing JSON metadata", ex);
            }
        } catch (SQLException ex) {
            throw new RuntimeException(
                    "Exception caught when searching in store table: \"" + schemaName + "\".\"" + tableName + "\"", ex);
        }
        return new EmbeddingSearchResult<>(embeddingMatches);
    }

    @Override
    public void removeAll(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids cannot be null or empty");
        }

        try (Connection conn = engine.getConnection()) {
            try (PreparedStatement preparedStatement = conn.prepareStatement(deleteQuery)) {
                Array array = conn.createArrayOf(
                        "uuid", ids.stream().map(UUID::fromString).toArray());
                preparedStatement.setArray(1, array);
                preparedStatement.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new RuntimeException(
                    String.format(
                            "Exception caught when deleting from vector store table: \"%s\".\"%s\"",
                            schemaName, tableName),
                    ex);
        }
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        addAll(singletonList(id), singletonList(embedding), singletonList(textSegment));
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> textSegments) {
        if (ids.size() != embeddings.size() || embeddings.size() != textSegments.size()) {
            throw new IllegalArgumentException(
                    "List parameters ids and embeddings and textSegments shouldn't be different sizes!");
        }
        try (Connection connection = engine.getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(insertQuery)) {
                PGvector.registerTypes(connection);
                for (int i = 0; i < ids.size(); i++) {
                    String id = ids.get(i);
                    Embedding embedding = embeddings.get(i);
                    TextSegment textSegment = textSegments.get(i);
                    String text = textSegment != null ? textSegment.text() : null;
                    Map<String, Object> embeddedMetadataCopy = textSegment != null
                            ? textSegment.metadata().toMap().entrySet().stream()
                                    .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()))
                            : null;
                    preparedStatement.setObject(1, UUID.fromString(id), Types.OTHER);
                    preparedStatement.setObject(2, new PGvector(embedding.vector()));
                    preparedStatement.setString(3, text);
                    int j = 0;
                    if (embeddedMetadataCopy != null && !embeddedMetadataCopy.isEmpty()) {
                        for (; j < metadataColumns.size(); j++) {
                            if (embeddedMetadataCopy.containsKey(metadataColumns.get(j))) {
                                preparedStatement.setObject(j + 4, embeddedMetadataCopy.remove(metadataColumns.get(j)));
                            } else {
                                preparedStatement.setObject(j + 4, null);
                            }
                        }
                        if (isNotNullOrEmpty(metadataJsonColumn)) {
                            // metadataJsonColumn should be the last column left
                            preparedStatement.setObject(
                                    j + 4, OBJECT_MAPPER.writeValueAsString(embeddedMetadataCopy), Types.OTHER);
                        }
                    } else {
                        for (; j < metadataColumns.size(); j++) {
                            preparedStatement.setObject(j + 4, null);
                        }
                        if (isNotNullOrEmpty(metadataJsonColumn)) {
                            preparedStatement.setObject(j + 4, null);
                        }
                    }
                    preparedStatement.addBatch();
                }
                preparedStatement.executeBatch();
            } catch (JsonProcessingException ex) {
                throw new RuntimeException("Exception caught when processing JSON metadata", ex);
            }

        } catch (SQLException ex) {
            throw new RuntimeException(
                    "Exception caught when inserting into vector store table: \"" + schemaName + "\".\"" + tableName
                            + "\"",
                    ex);
        }
    }

    /**
     * Create index in the vector store table
     *
     * @param index,        index to be applied
     * @param name,         name of the index
     * @param concurrently, CONCURRENTLY option
     */
    public void applyVectorIndex(BaseIndex index, String name, Boolean concurrently) {
        String function;
        if (index == null) {
            dropVectorIndex(null);
            return;
        }
        if (isNullOrBlank(name)) {
            if (isNotNullOrBlank(index.getName())) {
                name = index.getName();
            } else {
                name = tableName + BaseIndex.DEFAULT_INDEX_NAME_SUFFIX;
            }
        }

        try (Connection conn = engine.getConnection()) {
            if (index instanceof ScaNNIndex scaNNIndex) {
                conn.createStatement().executeQuery("CREATE EXTENSION IF NOT EXISTS alloydb_scann");
                function = scaNNIndex.getDistanceStrategy().getScannIndexFunction();
            } else {
                function = index.getDistanceStrategy().getIndexFunction();
            }

            String filter = (index.getPartialIndexes() != null
                            && index.getPartialIndexes().isEmpty())
                    ? String.format("WHERE %s", String.join(", ", index.getPartialIndexes()))
                    : "";
            String params = String.format("WITH %s", index.getIndexOptions());

            String concurrentlyString = concurrently ? "CONCURRENTLY" : "";

            String stmt = String.format(
                    "CREATE INDEX %s %s ON \"%s\".\"%s\" USING %s (%s %s) %s %s;",
                    concurrentlyString,
                    name,
                    schemaName,
                    tableName,
                    index.getIndexType(),
                    embeddingColumn,
                    function,
                    params,
                    filter);

            conn.createStatement().executeQuery(stmt);

        } catch (SQLException ex) {
            throw new RuntimeException(
                    "Exception caught when creating " + name + " index in vector store table: \"" + schemaName + "\".\""
                            + tableName + "\"",
                    ex);
        }
    }

    /**
     * remove index from the vector store table
     *
     * @param name, name of the index
     */
    public void dropVectorIndex(String name) {
        name = isNotNullOrBlank(name) ? name : tableName + BaseIndex.DEFAULT_INDEX_NAME_SUFFIX;
        String query = String.format("DROP INDEX IF EXISTS %s;", name);
        try (Connection conn = engine.getConnection()) {
            conn.createStatement().executeQuery(query);
        } catch (SQLException ex) {
            throw new RuntimeException(
                    "Exception caught when removing " + name + " index in vector store table: \"" + schemaName + "\".\""
                            + tableName + "\"",
                    ex);
        }
    }

    /**
     * re-index the vector store table
     *
     * @param name, name of the index
     */
    public void reindex(String name) {
        name = isNotNullOrBlank(name) ? name : tableName + BaseIndex.DEFAULT_INDEX_NAME_SUFFIX;
        String query = String.format("REINDEX INDEX %s;", name);
        try (Connection conn = engine.getConnection()) {
            conn.createStatement().executeQuery(query);
        } catch (SQLException ex) {
            throw new RuntimeException(
                    "Exception caught when reindexing " + name + " index in vector store table: \"" + schemaName
                            + "\".\"" + tableName + "\"",
                    ex);
        }
    }

    private double calculateRelevanceScore(double distance) {
        switch (distanceStrategy.name()) {
            case "EUCLIDEAN" -> {
                return (1d - (distance / Math.sqrt(2)));
            }
            case "COSINE_DISTANCE" -> {
                return RelevanceScore.fromCosineSimilarity(1d - distance);
            }
            case "INNER_PRODUCT" -> {
                if (distance > 0) {
                    return (1d - distance);
                }
                return (-1d * distance);
            }
            default -> {
                throw new UnsupportedOperationException(String.format(
                        "Unable to calculate relevance score for search function: %s ",
                        distanceStrategy.getSearchFunction()));
            }
        }
    }

    /**
     * Create a new {@link Builder}.
     *
     * @param engine    required {@link AlloyDBEngine}
     * @param tableName table to be used as embedding store
     * @return the new {@link Builder}.
     */
    public static Builder builder(AlloyDBEngine engine, String tableName) {
        return new Builder(engine, tableName);
    }

    /**
     * Builder which configures and creates instances of {@link AlloyDBEmbeddingStore}.
     */
    public static class Builder {

        private final AlloyDBEngine engine;
        private final String tableName;
        private String schemaName = "public";
        private String contentColumn = "content";
        private String embeddingColumn = "embedding";
        private String idColumn = "langchain4j_id";
        private List<String> metadataColumns = new ArrayList<>();
        private String metadataJsonColumn = "langchain4j_metadata";
        private List<String> ignoreMetadataColumnNames = new ArrayList<>();
        private DistanceStrategy distanceStrategy = DistanceStrategy.COSINE_DISTANCE;
        private QueryOptions queryOptions;

        /**
         * Constructor for Builder
         *
         * @param engine    required {@link AlloyDBEngine}
         * @param tableName table to be used as embedding store
         */
        public Builder(AlloyDBEngine engine, String tableName) {
            this.engine = engine;
            this.tableName = tableName;
        }

        /**
         * Schema Name
         *
         * @param schemaName The schema name (Default: "public")
         * @return this builder
         */
        public Builder schemaName(String schemaName) {
            this.schemaName = schemaName;
            return this;
        }

        /**
         * Content Column
         *
         * @param contentColumn create the content column (Default: "content")
         *                      with custom name
         * @return this builder
         */
        public Builder contentColumn(String contentColumn) {
            this.contentColumn = contentColumn;
            return this;
        }

        /**
         * Embedding Column
         *
         * @param embeddingColumn create the embedding (Default: "embedding")
         *                        column with custom name
         * @return this builder
         */
        public Builder embeddingColumn(String embeddingColumn) {
            this.embeddingColumn = embeddingColumn;
            return this;
        }

        /**
         * Id Column
         *
         * @param idColumn (Optional, Default: "langchain4j_id") Column to store
         *                 ids.
         * @return this builder
         */
        public Builder idColumn(String idColumn) {
            this.idColumn = idColumn;
            return this;
        }

        /**
         * Metadata Columns
         *
         * @param metadataColumns list of SQLAlchemy Columns to create for
         *                        custom metadata
         * @return this builder
         */
        public Builder metadataColumns(List<String> metadataColumns) {
            this.metadataColumns = metadataColumns;
            return this;
        }

        /**
         * Metadata JSON Column
         *
         * @param metadataJsonColumn (Default: "langchain_metadata") the column
         *                           to store extra metadata in
         * @return this builder
         */
        public Builder metadataJsonColumn(String metadataJsonColumn) {
            this.metadataJsonColumn = metadataJsonColumn;
            return this;
        }

        /**
         * Ignore Columns
         *
         * @param ignoreMetadataColumnNames (Optional) Column(s) to ignore in
         *                                  pre-existing tables for a document’s
         * @return this builder
         */
        public Builder ignoreMetadataColumnNames(List<String> ignoreMetadataColumnNames) {
            this.ignoreMetadataColumnNames = ignoreMetadataColumnNames;
            return this;
        }

        /**
         * Distance Strategy
         *
         * @param distanceStrategy (Defaults: COSINE_DISTANCE) Distance strategy
         *                         to use for vector similarity search
         * @return this builder
         */
        public Builder distanceStrategy(DistanceStrategy distanceStrategy) {
            this.distanceStrategy = distanceStrategy;
            return this;
        }

        /**
         * Query Options
         *
         * @param queryOptions (Optional) QueryOptions class with vector search
         *                     parameters
         * @return this builder
         */
        public Builder queryOptions(QueryOptions queryOptions) {
            this.queryOptions = queryOptions;
            return this;
        }

        /**
         * Builds an {@link AlloyDBEmbeddingStore} store with the configuration applied to this builder.
         *
         * @return A new {@link AlloyDBEmbeddingStore} instance
         */
        public AlloyDBEmbeddingStore build() {
            return new AlloyDBEmbeddingStore(this);
        }
    }
}
