package dev.langchain4j.community.store.memory.chat.yugabytedb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Schema configuration for YugabyteDB chat memory table
 *
 * This class encapsulates the database schema configuration for YugabyteDB chat memory storage,
 * including table structure, column names, and indexing strategy.
 */
public class YugabyteDBChatMemorySchema {

    /* Default configuration values */
    public static final String DEFAULT_TABLE_NAME = "chat_memory";
    public static final String DEFAULT_SCHEMA_NAME = "public";
    public static final String DEFAULT_ID_COLUMN = "id";
    public static final String DEFAULT_MESSAGES_COLUMN = "messages";
    public static final String DEFAULT_CREATED_AT_COLUMN = "created_at";
    public static final String DEFAULT_EXPIRES_AT_COLUMN = "expires_at";

    /* Schema configuration */
    private final String tableName;
    private final String schemaName;
    private final String idColumn;
    private final String messagesColumn;
    private final String createdAtColumn;
    private final String expiresAtColumn;
    private final boolean createTableIfNotExists;

    private YugabyteDBChatMemorySchema(Builder builder) {
        this.tableName = ensureNotBlank(builder.tableName, "tableName");
        this.schemaName = builder.schemaName;
        this.idColumn = ensureNotBlank(builder.idColumn, "idColumn");
        this.messagesColumn = ensureNotBlank(builder.messagesColumn, "messagesColumn");
        this.createdAtColumn = ensureNotBlank(builder.createdAtColumn, "createdAtColumn");
        this.expiresAtColumn = ensureNotBlank(builder.expiresAtColumn, "expiresAtColumn");
        this.createTableIfNotExists = builder.createTableIfNotExists;
    }

    public String getTableName() {
        return tableName;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public String getIdColumn() {
        return idColumn;
    }

    public String getMessagesColumn() {
        return messagesColumn;
    }

    public String getCreatedAtColumn() {
        return createdAtColumn;
    }

    public String getExpiresAtColumn() {
        return expiresAtColumn;
    }

    public boolean isCreateTableIfNotExists() {
        return createTableIfNotExists;
    }

    /**
     * Get the full table name including schema if specified
     */
    public String getFullTableName() {
        return schemaName != null && !schemaName.trim().isEmpty() ? schemaName + "." + tableName : tableName;
    }

    /**
     * Generate CREATE TABLE SQL
     */
    public String getCreateTableSql() {
        return String.format(
                "CREATE TABLE IF NOT EXISTS %s (" + "%s TEXT PRIMARY KEY, "
                        + "%s JSONB NOT NULL, "
                        + "%s TIMESTAMP WITH TIME ZONE DEFAULT NOW(), "
                        + "%s TIMESTAMP WITH TIME ZONE"
                        + ")",
                getFullTableName(), idColumn, messagesColumn, createdAtColumn, expiresAtColumn);
    }

    /**
     * Generate CREATE INDEX SQL for expires_at column to optimize TTL cleanup
     */
    public String getCreateIndexSql() {
        return String.format(
                "CREATE INDEX IF NOT EXISTS %s_%s_idx ON %s (%s) WHERE %s IS NOT NULL",
                tableName, expiresAtColumn, getFullTableName(), expiresAtColumn, expiresAtColumn);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String tableName = DEFAULT_TABLE_NAME;
        private String schemaName = DEFAULT_SCHEMA_NAME;
        private String idColumn = DEFAULT_ID_COLUMN;
        private String messagesColumn = DEFAULT_MESSAGES_COLUMN;
        private String createdAtColumn = DEFAULT_CREATED_AT_COLUMN;
        private String expiresAtColumn = DEFAULT_EXPIRES_AT_COLUMN;
        private boolean createTableIfNotExists = true;

        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder schemaName(String schemaName) {
            this.schemaName = schemaName;
            return this;
        }

        public Builder idColumn(String idColumn) {
            this.idColumn = idColumn;
            return this;
        }

        public Builder messagesColumn(String messagesColumn) {
            this.messagesColumn = messagesColumn;
            return this;
        }

        public Builder createdAtColumn(String createdAtColumn) {
            this.createdAtColumn = createdAtColumn;
            return this;
        }

        public Builder expiresAtColumn(String expiresAtColumn) {
            this.expiresAtColumn = expiresAtColumn;
            return this;
        }

        public Builder createTableIfNotExists(boolean createTableIfNotExists) {
            this.createTableIfNotExists = createTableIfNotExists;
            return this;
        }

        public YugabyteDBChatMemorySchema build() {
            return new YugabyteDBChatMemorySchema(this);
        }
    }
}
