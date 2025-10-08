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
 * Metadata handler for COMBINED_JSON storage mode in YugabyteDB.
 * <p>
 * Stores metadata as JSON text in a single column. Provides flexibility for dynamic
 * metadata schemas but with limited indexing capabilities compared to JSONB.
 */
class JsonMetadataHandler implements MetadataHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(INDENT_OUTPUT);

    private final MetadataColumnDefinition columnDefinition;
    private final String columnName;
    private final JsonFilterMapper filterMapper;
    private final List<String> indexes;

    /**
     * Creates a new JsonMetadataHandler with the specified configuration.
     *
     * @param config the metadata storage configuration
     */
    public JsonMetadataHandler(MetadataStorageConfig config) {
        List<String> definition = ensureNotEmpty(config.columnDefinitions(), "Metadata definition");
        if (definition.size() > 1) {
            throw new IllegalArgumentException(
                    "Metadata definition should be a single column definition, " + "example: metadata JSON NULL");
        }
        this.columnDefinition = MetadataColumnDefinition.parse(definition.get(0));
        this.columnName = this.columnDefinition.getName();
        this.filterMapper = new JsonFilterMapper(columnName);
        this.indexes = getOrDefault(config.indexes(), Collections.emptyList());

        // Validate that no indexes are configured for JSON storage
        if (!this.indexes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Indexes are not supported for COMBINED_JSON storage mode. "
                            + "JSON has limited indexing capabilities. Use COMBINED_JSONB instead for better indexing support.");
        }
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
        // No indexes to create for JSON storage mode
        // Index validation is done in constructor to fail fast
    }

    @Override
    public String whereClause(Filter filter) {
        return filterMapper.map(filter);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Metadata fromResultSet(ResultSet resultSet) {
        try {
            String metadataJson =
                    getOrDefault(resultSet.getString(columnsNames().get(0)), "{}");
            return new Metadata(OBJECT_MAPPER.readValue(metadataJson, Map.class));
        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Failed to parse metadata JSON from result set", e);
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
            throw new SQLException("Failed to serialize metadata to JSON", e);
        }
    }

    @Override
    public int setFilterParameters(PreparedStatement statement, Filter filter, int parameterStartIndex)
            throws SQLException {
        // For JSON storage, filter parameters are handled by the filter mapper
        // No additional parameter binding needed as values are embedded in the WHERE clause
        return parameterStartIndex;
    }
}
