package dev.langchain4j.community.data.document.loader.cloudsql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.community.store.embedding.cloudsql.PostgresEngine;
import dev.langchain4j.data.document.DefaultDocument;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * PostgresLoader
 * <p>
 * This loader allows you to retrieve data from Cloud SQL using either a custom
 * SQL query or a table name. You can specify which columns should be used for
 * the document's content and which for its metadata. Additionally, you can
 * format the content using predefined formats (CSV, text, JSON, YAML) or a
 * custom formatter. Metadata can be loaded from specified columns or from a
 * JSON column.
 * </p>
 */
public class PostgresLoader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_METADATA_COL = "langchain_metadata";

    private final PostgresEngine engine;
    private final String query;
    private final List<String> contentColumns;
    private final List<String> metadataColumns;
    private final BiFunction<Map<String, Object>, List<String>, String> formatter;
    private final String metadataJsonColumn;

    /**
     * Create a new {@link PostgresLoader} from the Builder.
     *
     * @param builder the builder.
     */
    private PostgresLoader(Builder builder) {
        this.engine = builder.engine;
        this.query = builder.query;
        this.formatter = builder.formatter;
        this.contentColumns = builder.contentColumns;
        this.metadataColumns = builder.metadataColumns;
        this.metadataJsonColumn = builder.metadataJsonColumn;
    }

    /**
     * Formats the row data into a text string.
     *
     * @param row            The row data as a map of column names to values.
     * @param contentColumns The list of columns to use for content.
     * @return The formatted text string.
     */
    private static String textFormatter(Map<String, Object> row, List<String> contentColumns) {
        StringBuilder sb = new StringBuilder();
        for (String column : contentColumns) {
            if (row.containsKey(column)) {
                sb.append(row.get(column)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    /**
     * Formats the row data into a CSV string.
     *
     * @param row            The row data as a map of column names to values.
     * @param contentColumns The list of columns to use for content.
     * @return The formatted CSV string.
     */
    private static String csvFormatter(Map<String, Object> row, List<String> contentColumns) {
        StringBuilder sb = new StringBuilder();
        for (String column : contentColumns) {
            if (row.containsKey(column)) {
                sb.append(row.get(column)).append(", ");
            }
        }
        return sb.toString().trim().replaceAll(", $", ""); // Remove trailing comma
    }

    /**
     * Formats the row data into a YAML string.
     *
     * @param row            The row data as a map of column names to values.
     * @param contentColumns The list of columns to use for content.
     * @return The formatted YAML string.
     */
    private static String yamlFormatter(Map<String, Object> row, List<String> contentColumns) {
        StringBuilder sb = new StringBuilder();
        for (String column : contentColumns) {
            if (row.containsKey(column)) {
                sb.append(column).append(": ").append(row.get(column)).append("\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * Formats the row data into a JSON string.
     *
     * @param row            The row data as a map of column names to values.
     * @param contentColumns The list of columns to use for content.
     * @return The formatted JSON string.
     */
    private static String jsonFormatter(Map<String, Object> row, List<String> contentColumns) {
        ObjectNode json = OBJECT_MAPPER.createObjectNode();
        for (String column : contentColumns) {
            if (row.containsKey(column)) {
                json.put(column, (String) row.get(column));
            }
        }
        return json.toString();
    }

    /**
     * Executes the configured SQL query against the Cloud SQL database and
     * transforms the result set into a list of {@link Document} objects.
     * <p>
     * Each row in the result set is processed according to the configured
     * content columns, metadata columns, and formatter. The content is
     * extracted and formatted, and metadata is assembled from the specified
     * columns or the JSON metadata column.
     * </p>
     *
     * @return A list of {@link Document} objects, where each document
     * represents a row from the database result set.
     * @throws SQLException     If a database error occurs during the execution of
     *                          the query or while processing the result set. This could include issues
     *                          such as connection problems, SQL syntax errors, or data retrieval
     *                          failures.
     * @throws RuntimeException If there's an error parsing the JSON metadata
     *                          column, indicating an issue with the JSON structure.
     */
    public List<Document> load() throws SQLException {
        List<Document> documents = new ArrayList<>();
        try (Connection pool = engine.getConnection();
                PreparedStatement statement = pool.prepareStatement(query)) {
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                Map<String, Object> rowData = new HashMap<>();
                for (String column : contentColumns) {
                    rowData.put(column, resultSet.getString(column));
                }
                for (String column : metadataColumns) {
                    rowData.put(column, resultSet.getObject(column));
                }
                if (metadataJsonColumn != null) {
                    rowData.put(metadataJsonColumn, resultSet.getObject(metadataJsonColumn));
                }
                Document doc = parseDocFromRow(rowData);
                documents.add(doc);
            }
        }
        return documents;
    }

    /**
     * Parses a {@link Document} from a row of data.
     *
     * @param row The row data as a map of column names to values.
     * @return The parsed Document.
     */
    private Document parseDocFromRow(Map<String, Object> row) {
        String pageContent = formatter.apply(row, contentColumns);
        Map<String, Object> metaDataMap = new HashMap<>();
        if (metadataJsonColumn != null && row.containsKey(metadataJsonColumn)) {
            try {
                metaDataMap.putAll(
                        OBJECT_MAPPER.readValue(row.get(metadataJsonColumn).toString(), Map.class));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(
                        "Failed to parse JSON: " + e.getMessage()
                                + ". Ensure metadata JSON structure matches the expected format.",
                        e);
            }
        }

        for (String column : metadataColumns) {
            if (row.containsKey(column) && !column.equals(metadataJsonColumn)) {
                metaDataMap.put(column, row.get(column));
            }
        }
        Metadata metadata = Metadata.from(metaDataMap);
        return new DefaultDocument(pageContent, metadata);
    }

    /**
     * Create a new {@link Builder}.
     *
     * @param engine The Cloud SQL Engine
     * @return the new {@link Builder}.
     */
    public static Builder builder(PostgresEngine engine) {
        return new Builder(engine);
    }

    /**
     * Builder for {@link PostgresLoader}.
     */
    public static class Builder {

        private final PostgresEngine engine;
        private String tableName;
        private String query;
        private String metadataJsonColumn;
        private String schemaName = "public";
        private List<String> contentColumns;
        private List<String> metadataColumns;
        private String format;
        private BiFunction<Map<String, Object>, List<String>, String> formatter;

        /**
         * Construct a LoaderBuilder.
         *
         * @param engine The PostgresEngine.
         */
        public Builder(PostgresEngine engine) {
            this.engine = engine;
        }

        /**
         * Sets the schema name for the table. Defaults to "public".
         *
         * @param schemaName The schema name.
         * @return This Builder.
         */
        public Builder schemaName(String schemaName) {
            this.schemaName = schemaName;
            return this;
        }

        /**
         * Sets the SQL query to execute. If not provided, a default query is
         * generated from the table name.
         *
         * @param query The SQL query.
         * @return This Builder.
         */
        public Builder query(String query) {
            this.query = query;
            return this;
        }

        /**
         * Sets the table name to load data from. If not provided, a custom
         * query must be specified.
         *
         * @param tableName The table name.
         * @return This Builder.
         */
        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        /**
         * Sets a custom formatter to convert row data into document content.
         *
         * @param formatter The formatter function.
         * @return This Builder.
         */
        public Builder formatter(BiFunction<Map<String, Object>, List<String>, String> formatter) {
            this.formatter = formatter;
            return this;
        }

        /**
         * Sets the format for the document content. Predefined formats are
         * "csv", "text", "JSON", and "YAML". Only one of format or formatter
         * should be specified.
         *
         * @param format The format string.
         * @return This Builder.
         */
        public Builder format(String format) {
            this.format = format;
            return this;
        }

        /**
         * Sets the list of columns to use for the document content.
         *
         * @param contentColumns The content column names.
         * @return This Builder.
         */
        public Builder contentColumns(List<String> contentColumns) {
            this.contentColumns = contentColumns;
            return this;
        }

        /**
         * Sets the list of columns to use for the document metadata.
         *
         * @param metadataColumns The metadata column names.
         * @return This Builder.
         */
        public Builder metadataColumns(List<String> metadataColumns) {
            this.metadataColumns = metadataColumns;
            return this;
        }

        /**
         * Sets the column name containing JSON metadata.
         *
         * @param metadataJsonColumn The JSON metadata column name.
         * @return This Builder.
         */
        public Builder metadataJsonColumn(String metadataJsonColumn) {
            this.metadataJsonColumn = metadataJsonColumn;
            return this;
        }

        /**
         * Builds an {@link PostgresLoader} with the configuration applied to
         * this builder.
         *
         * @return The built {@link PostgresLoader} instance.
         * @throws IllegalArgumentException if neither query nor tableName is
         *                                  specified, or if both format and formatter are specified, or if
         *                                  specified column not found.
         * @throws SQLException             if a database error occurs.
         */
        public PostgresLoader build() throws SQLException {
            if ((this.query == null || this.query.isEmpty()) && (this.tableName == null || this.tableName.isEmpty())) {
                throw new IllegalArgumentException("Either query or tableName must be specified.");
            }
            if (query == null) {
                query = String.format("SELECT * FROM \"%s\".\"%s\"", schemaName, tableName);
            }

            if (format != null && formatter != null) {
                throw new IllegalArgumentException("Only one of 'format' or 'formatter' should be specified.");
            }

            if (format != null) {
                switch (format) {
                    case "csv":
                        this.formatter = PostgresLoader::csvFormatter;
                        break;
                    case "text":
                        this.formatter = PostgresLoader::textFormatter;
                        break;
                    case "JSON":
                        this.formatter = PostgresLoader::jsonFormatter;
                        break;
                    case "YAML":
                        this.formatter = PostgresLoader::yamlFormatter;
                        break;
                    default:
                        throw new IllegalArgumentException("format must be type: 'csv', 'text', 'JSON', 'YAML'");
                }
            } else if (formatter == null) {
                this.formatter = PostgresLoader::textFormatter;
            }

            List<String> columnNames = new ArrayList<>();
            try (Connection pool = engine.getConnection();
                    PreparedStatement statement = pool.prepareStatement(query)) {
                statement.setMaxRows(1);
                ResultSet resultSet = statement.executeQuery();
                for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                    columnNames.add(resultSet.getMetaData().getColumnName(i));
                }
            }

            contentColumns =
                    contentColumns == null || contentColumns.isEmpty() ? List.of(columnNames.get(0)) : contentColumns;
            metadataColumns = metadataColumns == null || metadataColumns.isEmpty()
                    ? columnNames.stream()
                            .filter(col -> !contentColumns.contains(col))
                            .toList()
                    : metadataColumns;

            if (metadataJsonColumn != null && !columnNames.contains(metadataJsonColumn)) {
                throw new IllegalArgumentException(
                        String.format("Column %s not found in query result %s.", metadataJsonColumn, columnNames));
            }
            if (metadataJsonColumn == null && columnNames.contains(DEFAULT_METADATA_COL)) {
                metadataJsonColumn = DEFAULT_METADATA_COL;
            }

            List<String> allNames = new ArrayList<>(contentColumns);
            allNames.addAll(metadataColumns);
            for (String name : allNames) {
                if (!columnNames.contains(name)) {
                    throw new IllegalArgumentException(
                            String.format("Column %s not found in query result %s.", name, columnNames));
                }
            }
            return new PostgresLoader(this);
        }
    }
}
