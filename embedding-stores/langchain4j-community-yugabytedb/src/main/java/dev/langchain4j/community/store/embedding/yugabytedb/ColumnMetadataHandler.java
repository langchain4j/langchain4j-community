package dev.langchain4j.community.store.embedding.yugabytedb;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.filter.Filter;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Metadata handler for COLUMN_PER_KEY storage mode in YugabyteDB.
 * <p>
 * Stores each metadata key as a separate database column with proper SQL
 * typing.
 * This provides optimal query performance for known metadata schemas but
 * requires
 * predefined column definitions.
 */
class ColumnMetadataHandler implements MetadataHandler {

    private final List<MetadataColumnDefinition> columnsDefinition;
    private final List<String> columnsName;
    private final YugabyteDBFilterMapper filterMapper;
    private final List<String> indexes;
    private final String indexType;

    /**
     * Creates a new ColumnMetadataHandler with the specified configuration.
     *
     * @param config the metadata storage configuration
     */
    public ColumnMetadataHandler(MetadataStorageConfig config) {
        List<String> columnsDefinitionList = ensureNotEmpty(config.columnDefinitions(), "Metadata definition");
        this.columnsDefinition = columnsDefinitionList.stream()
                .map(MetadataColumnDefinition::parse)
                .collect(Collectors.toList());
        this.columnsName = columnsDefinition.stream()
                .map(MetadataColumnDefinition::getName)
                .collect(Collectors.toList());
        this.filterMapper = new ColumnFilterMapper();
        this.indexes = getOrDefault(config.indexes(), Collections.emptyList());
        this.indexType = config.indexType();
    }

    @Override
    public String columnDefinitionsString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columnsDefinition.size(); i++) {
            sb.append(columnsDefinition.get(i).getFullDefinition());
            if (i < columnsDefinition.size() - 1) {
                sb.append(",");
            }
        }
        String result = sb.toString();
        return result;
    }

    @Override
    public List<String> columnsNames() {
        return this.columnsName;
    }

    @Override
    public void createMetadataIndexes(Statement statement, String table) throws SQLException {
        String indexTypeSql = indexType == null ? "" : "USING " + indexType;
        this.indexes.stream().map(String::trim).forEach(index -> {
            // Clean table name for index naming (remove schema prefixes, dots, etc.)
            String cleanTableName = table.replaceAll("[^a-zA-Z0-9_]", "_");
            String indexName = String.format("idx_%s_%s", cleanTableName, index);
            String indexSql = String.format(
                    "CREATE INDEX IF NOT EXISTS %s ON %s %s ( %s )", indexName, table, indexTypeSql, index);
            try {
                statement.executeUpdate(indexSql);
            } catch (SQLException e) {
                throw new RuntimeException(String.format("Cannot create index %s: %s", index, e.getMessage()), e);
            }
        });
    }

    @Override
    public String insertClause() {
        return this.columnsName.stream()
                .map(c -> String.format("%s = EXCLUDED.%s", c, c))
                .collect(Collectors.joining(","));
    }

    @Override
    public int setMetadata(PreparedStatement upsertStmt, int parameterInitialIndex, Metadata metadata)
            throws SQLException {
        Map<String, Object> metadataMap = metadata.toMap();
        int i = 0;
        // Only column names fields will be stored
        for (MetadataColumnDefinition columnDef : this.columnsDefinition) {
            String columnName = columnDef.getName();
            Object value = metadataMap.get(columnName);

            // Special handling for UUID type
            if (value instanceof String && "uuid".equalsIgnoreCase(columnDef.getType())) {
                try {
                    value = java.util.UUID.fromString((String) value);
                } catch (IllegalArgumentException e) {
                    // It's a string but not a valid UUID. Let the DB handle the error.
                }
            }

            if (value == null) {
                upsertStmt.setNull(parameterInitialIndex + i, getSqlType(columnDef.getType()));
            } else {
                upsertStmt.setObject(parameterInitialIndex + i, value);
            }
            i++;
        }
        return parameterInitialIndex + i;
    }

    @Override
    public int setFilterParameters(PreparedStatement statement, Filter filter, int parameterStartIndex)
            throws SQLException {
        // For column-based storage, filter parameters are handled by the filter mapper
        // No additional parameter binding needed as values are embedded in the WHERE
        // clause
        return parameterStartIndex;
    }

    @Override
    public String whereClause(Filter filter) {
        return filterMapper.map(filter);
    }

    @Override
    public Metadata fromResultSet(ResultSet resultSet) {
        try {
            Map<String, Object> metadataMap = new HashMap<>();
            for (String columnName : this.columnsName) {
                Object value = resultSet.getObject(columnName);
                if (value != null) {
                    metadataMap.put(columnName, value);
                }
            }
            return new Metadata(metadataMap);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to extract metadata from result set", e);
        }
    }

    private int getSqlType(String type) {
        String upperType = type.toUpperCase();
        if (upperType.contains("UUID")) return Types.OTHER;
        if (upperType.contains("TEXT") || upperType.contains("VARCHAR")) return Types.VARCHAR;
        if (upperType.contains("INT")) return Types.INTEGER;
        if (upperType.contains("DOUBLE")) return Types.DOUBLE;
        if (upperType.contains("FLOAT")) return Types.FLOAT;
        if (upperType.contains("BOOL")) return Types.BOOLEAN;
        if (upperType.contains("TIMESTAMP")) return Types.TIMESTAMP;
        if (upperType.contains("DATE")) return Types.DATE;
        return Types.OTHER;
    }
}
