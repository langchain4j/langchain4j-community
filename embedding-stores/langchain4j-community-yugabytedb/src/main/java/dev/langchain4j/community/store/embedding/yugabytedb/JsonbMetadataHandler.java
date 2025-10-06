package dev.langchain4j.community.store.embedding.yugabytedb;

import static com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.toStringValueMap;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import java.sql.*;
import java.util.*;

/**
 * Metadata handler for COMBINED_JSONB storage mode in YugabyteDB.
 * <p>
 * Stores metadata as binary JSON (JSONB) which provides:
 * - Flexible schema for dynamic metadata
 * - Efficient storage and query performance
 * - Support for PostgreSQL's advanced JSONB operators
 * - GIN indexing capabilities for fast queries
 * <p>
 * This is the recommended handler for most use cases as it provides
 * the best balance of flexibility and performance.
 */
class JsonbMetadataHandler implements MetadataHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);

    private final MetadataColumnDefinition columnDefinition;
    private final String columnName;
    private final JsonFilterMapper filterMapper;
    private final List<String> indexes;
    private final String indexType;

    /**
     * Creates a new JsonbMetadataHandler with the specified configuration.
     *
     * @param config the metadata storage configuration
     */
    public JsonbMetadataHandler(MetadataStorageConfig config) {
        List<String> definition = ensureNotEmpty(config.columnDefinitions(), "Metadata definition");
        if (definition.size() > 1) {
            throw new IllegalArgumentException(
                    "Metadata definition should be a single column definition, " + "example: metadata JSONB NULL");
        }

        this.columnDefinition = MetadataColumnDefinition.parse(definition.get(0));
        if (!this.columnDefinition.getType().equals("jsonb")) {
            throw new RuntimeException("Column definition type should be JSONB for COMBINED_JSONB storage mode");
        }
        this.columnName = this.columnDefinition.getName();
        this.filterMapper = new JsonFilterMapper(columnName);
        this.indexes = getOrDefault(config.indexes(), Collections.emptyList());
        this.indexType = getOrDefault(config.indexType(), "GIN");
    }

    @Override
    public String columnDefinitionsString() {
        return columnDefinition.getFullDefinition();
    }

    @Override
    public List<String> columnsNames() {
        return Collections.singletonList(this.columnName);
    }

    @Override
    public void createMetadataIndexes(Statement statement, String table) throws SQLException {
        String indexTypeSql = indexType != null ? "USING " + indexType : "";
        for (String index : this.indexes) {
            String indexName = formatIndexName(table, index.trim());
            try {
                String indexSql = String.format(
                        "CREATE INDEX IF NOT EXISTS %s ON %s %s (%s)", indexName, table, indexTypeSql, index);
                statement.executeUpdate(indexSql);
            } catch (SQLException e) {
                throw new RuntimeException(String.format("Cannot create index %s: %s", indexName, e.getMessage()), e);
            }
        }
    }

    @Override
    public String whereClause(Filter filter) {
        return filterMapper.map(filter);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Metadata fromResultSet(ResultSet resultSet) throws SQLException {
        try {
            String metadataJson =
                    getOrDefault(resultSet.getString(columnsNames().get(0)), "{}");
            return new Metadata(OBJECT_MAPPER.readValue(metadataJson, Map.class));
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to parse metadata JSONB from result set", e);
        }
    }

    @Override
    public String insertClause() {
        return String.format("%s = EXCLUDED.%s", this.columnName, this.columnName);
    }

    @Override
    public int setMetadata(PreparedStatement upsertStmt, int parameterInitialIndex, Metadata metadata)
            throws SQLException {
        try {
            String metadataJson = OBJECT_MAPPER.writeValueAsString(toStringValueMap(metadata.toMap()));
            upsertStmt.setObject(parameterInitialIndex, metadataJson, Types.OTHER);
            return parameterInitialIndex + 1;
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize metadata to JSONB", e);
        }
    }

    @Override
    public int setFilterParameters(PreparedStatement statement, Filter filter, int parameterStartIndex)
            throws SQLException {
        // For JSONB storage, filter parameters are handled by the filter mapper
        // No additional parameter binding needed as values are embedded in the WHERE clause
        return parameterStartIndex;
    }

    /**
     * Formats an index name based on table and index definition.
     * Handles both simple column names and complex JSONB path expressions.
     */
    private String formatIndexName(String table, String index) {
        // Clean table name for index naming (remove schema prefixes, dots, etc.)
        String cleanTableName = table.replaceAll("[^a-zA-Z0-9_]", "_");
        String baseIndexName;

        if (index.contains("->")) {
            // For JSONB path expressions like "(metadata->>'category')"
            // Extract the path and create a clean index name
            String path = index.substring(index.indexOf("->") + 2);
            path = path.replaceAll("[^a-zA-Z0-9_]", ""); // Remove special characters
            baseIndexName = cleanTableName + "_" + columnName + "_" + path + "_idx";
        } else {
            // For simple column names or full column reference
            String cleanIndex = index.replaceAll("[^a-zA-Z0-9_]", "");
            baseIndexName = cleanTableName + "_" + cleanIndex + "_idx";
        }

        return baseIndexName;
    }
}
