package dev.langchain4j.community.store.memory.chat.cockroachdb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Schema for the chat-history table used by {@link CockroachDbChatMemoryStore}.
 *
 * <p>Default DDL:
 * <pre>
 *   CREATE TABLE message_store (
 *     id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
 *     session_id TEXT NOT NULL,
 *     message JSONB NOT NULL,
 *     created_at TIMESTAMPTZ NOT NULL DEFAULT now()
 *   );
 *   CREATE INDEX message_store_session_idx ON message_store (session_id, created_at);
 * </pre>
 *
 * <p>If {@link Builder#ttl(Duration)} is set, an ALTER TABLE statement enables
 * CockroachDB's row-level TTL via {@code ttl_expiration_expression} (dollar-quoted
 * to avoid full-table rewrites) and {@code ttl_job_cron}.
 */
public class CockroachDbChatMemorySchema {

    public static final String DEFAULT_TABLE_NAME = "message_store";
    public static final String DEFAULT_SCHEMA_NAME = "public";
    public static final String DEFAULT_ID_COLUMN = "id";
    public static final String DEFAULT_SESSION_COLUMN = "session_id";
    public static final String DEFAULT_MESSAGE_COLUMN = "message";
    public static final String DEFAULT_CREATED_AT_COLUMN = "created_at";
    public static final String DEFAULT_IDX_COLUMN = "idx";
    public static final String DEFAULT_TTL_CRON = "@daily";

    private static final Pattern CRON_PATTERN = Pattern.compile("^[@\\w\\s\\*/,\\-]+$");

    private final String tableName;
    private final String schemaName;
    private final String idColumn;
    private final String sessionColumn;
    private final String messageColumn;
    private final String createdAtColumn;
    private final String idxColumn;
    private final boolean createTableIfNotExists;
    private final Duration ttl;
    private final String ttlJobCron;

    private CockroachDbChatMemorySchema(Builder b) {
        this.tableName = ensureNotBlank(b.tableName, "tableName");
        this.schemaName = b.schemaName;
        this.idColumn = ensureNotBlank(b.idColumn, "idColumn");
        this.sessionColumn = ensureNotBlank(b.sessionColumn, "sessionColumn");
        this.messageColumn = ensureNotBlank(b.messageColumn, "messageColumn");
        this.createdAtColumn = ensureNotBlank(b.createdAtColumn, "createdAtColumn");
        this.idxColumn = ensureNotBlank(b.idxColumn, "idxColumn");
        this.createTableIfNotExists = b.createTableIfNotExists;
        this.ttl = b.ttl;
        this.ttlJobCron = b.ttlJobCron;
        if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        if (!CRON_PATTERN.matcher(ttlJobCron).matches()) {
            throw new IllegalArgumentException("ttlJobCron contains invalid characters: " + ttlJobCron);
        }
    }

    public static Builder builder() {
        return new Builder();
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

    public String getSessionColumn() {
        return sessionColumn;
    }

    public String getMessageColumn() {
        return messageColumn;
    }

    public String getCreatedAtColumn() {
        return createdAtColumn;
    }

    public String getIdxColumn() {
        return idxColumn;
    }

    public boolean isCreateTableIfNotExists() {
        return createTableIfNotExists;
    }

    public Duration getTtl() {
        return ttl;
    }

    public String getTtlJobCron() {
        return ttlJobCron;
    }

    public String getFullTableName() {
        return schemaName != null && !schemaName.trim().isEmpty() ? schemaName + "." + tableName : tableName;
    }

    public String getCreateTableSql() {
        return String.format(
                "CREATE TABLE IF NOT EXISTS %s (" + "%s UUID PRIMARY KEY DEFAULT gen_random_uuid(), "
                        + "%s TEXT NOT NULL, "
                        + "%s JSONB NOT NULL, "
                        + "%s INT NOT NULL DEFAULT 0, "
                        + "%s TIMESTAMPTZ NOT NULL DEFAULT now()"
                        + ")",
                getFullTableName(), idColumn, sessionColumn, messageColumn, idxColumn, createdAtColumn);
    }

    public String getCreateSessionIndexSql() {
        return String.format(
                "CREATE INDEX IF NOT EXISTS %s_session_idx ON %s (%s, %s, %s)",
                tableName, getFullTableName(), sessionColumn, createdAtColumn, idxColumn);
    }

    /**
     * @return the {@code ALTER TABLE ... SET (ttl_…)} statement, or {@code null} if TTL is disabled.
     */
    public String getEnableTtlSql() {
        if (ttl == null) return null;
        String interval = formatInterval(ttl);
        // Dollar-quote the expression so the embedded INTERVAL literal doesn't have to be escaped.
        return String.format(
                "ALTER TABLE %s SET (" + "ttl_expiration_expression = $$(%s + '%s')$$, " + "ttl_job_cron = '%s'" + ")",
                getFullTableName(), createdAtColumn, interval, ttlJobCron);
    }

    public String getDisableTtlSql() {
        return String.format("ALTER TABLE %s RESET (ttl)", getFullTableName());
    }

    private static String formatInterval(Duration d) {
        long seconds = d.toSeconds();
        if (seconds % 86_400 == 0) return (seconds / 86_400) + " days";
        if (seconds % 3_600 == 0) return (seconds / 3_600) + " hours";
        if (seconds % 60 == 0) return (seconds / 60) + " minutes";
        return seconds + " seconds";
    }

    public static class Builder {
        private String tableName = DEFAULT_TABLE_NAME;
        private String schemaName = DEFAULT_SCHEMA_NAME;
        private String idColumn = DEFAULT_ID_COLUMN;
        private String sessionColumn = DEFAULT_SESSION_COLUMN;
        private String messageColumn = DEFAULT_MESSAGE_COLUMN;
        private String createdAtColumn = DEFAULT_CREATED_AT_COLUMN;
        private String idxColumn = DEFAULT_IDX_COLUMN;
        private boolean createTableIfNotExists = true;
        private Duration ttl;
        private String ttlJobCron = DEFAULT_TTL_CRON;

        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder schemaName(String schemaName) {
            this.schemaName = schemaName;
            return this;
        }

        public Builder idColumn(String c) {
            this.idColumn = c;
            return this;
        }

        public Builder sessionColumn(String c) {
            this.sessionColumn = c;
            return this;
        }

        public Builder messageColumn(String c) {
            this.messageColumn = c;
            return this;
        }

        public Builder createdAtColumn(String c) {
            this.createdAtColumn = c;
            return this;
        }

        public Builder idxColumn(String c) {
            this.idxColumn = c;
            return this;
        }

        public Builder createTableIfNotExists(boolean v) {
            this.createTableIfNotExists = v;
            return this;
        }

        public Builder ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder ttlJobCron(String cron) {
            this.ttlJobCron = cron;
            return this;
        }

        public CockroachDbChatMemorySchema build() {
            return new CockroachDbChatMemorySchema(this);
        }
    }
}
