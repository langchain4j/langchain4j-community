package dev.langchain4j.community.store.memory.chat.cockroachdb;

import dev.langchain4j.community.store.embedding.cockroachdb.CockroachDbEngine;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CockroachDB-backed {@link ChatMemoryStore}. Each {@link ChatMessage} is stored
 * as its own row, serialized to JSONB via {@link ChatMessageSerializer}, with a
 * {@code created_at} timestamp that preserves chronological order and feeds
 * optional row-level TTL.
 *
 * <p>{@link #updateMessages(Object, List)} replaces the full session: it deletes
 * existing rows for the memory id and re-inserts the supplied list inside a
 * transaction.
 */
public class CockroachDbChatMemoryStore implements ChatMemoryStore {

    private static final Logger logger = LoggerFactory.getLogger(CockroachDbChatMemoryStore.class);

    private final CockroachDbEngine engine;
    private final CockroachDbChatMemorySchema schema;

    public CockroachDbChatMemoryStore(Builder builder) {
        this.engine = builder.engine;
        this.schema = builder.schema;
        if (schema.isCreateTableIfNotExists()) {
            createTableIfNotExists();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String sessionId = String.valueOf(memoryId);
        String sql = String.format(
                "SELECT %s FROM %s WHERE %s = ? ORDER BY %s ASC, %s ASC",
                schema.getMessageColumn(),
                schema.getFullTableName(),
                schema.getSessionColumn(),
                schema.getCreatedAtColumn(),
                schema.getIdxColumn());

        try (Connection conn = engine.getConnection();
                PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, sessionId);
            try (ResultSet rs = st.executeQuery()) {
                List<ChatMessage> messages = new ArrayList<>();
                while (rs.next()) {
                    messages.add(ChatMessageDeserializer.messageFromJson(rs.getString(1)));
                }
                return messages;
            }
        } catch (SQLException e) {
            throw new CockroachDbChatMemoryStoreException("Failed to load chat memory for " + sessionId, e);
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String sessionId = String.valueOf(memoryId);
        String deleteSql =
                String.format("DELETE FROM %s WHERE %s = ?", schema.getFullTableName(), schema.getSessionColumn());
        String insertSql = String.format(
                "INSERT INTO %s (%s, %s, %s) VALUES (?, ?::jsonb, ?)",
                schema.getFullTableName(), schema.getSessionColumn(), schema.getMessageColumn(), schema.getIdxColumn());

        try (Connection conn = engine.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement(deleteSql)) {
                del.setString(1, sessionId);
                del.executeUpdate();
            }
            if (messages != null && !messages.isEmpty()) {
                try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                    int idx = 0;
                    for (ChatMessage m : messages) {
                        ins.setString(1, sessionId);
                        ins.setString(2, ChatMessageSerializer.messageToJson(m));
                        ins.setInt(3, idx++);
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
            }
            conn.commit();
        } catch (SQLException e) {
            throw new CockroachDbChatMemoryStoreException("Failed to update chat memory for " + sessionId, e);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String sessionId = String.valueOf(memoryId);
        String sql = String.format("DELETE FROM %s WHERE %s = ?", schema.getFullTableName(), schema.getSessionColumn());
        try (Connection conn = engine.getConnection();
                PreparedStatement st = conn.prepareStatement(sql)) {
            st.setString(1, sessionId);
            st.executeUpdate();
        } catch (SQLException e) {
            throw new CockroachDbChatMemoryStoreException("Failed to delete chat memory for " + sessionId, e);
        }
    }

    public void createTableIfNotExists() {
        try (Connection conn = engine.getConnection();
                Statement st = conn.createStatement()) {
            st.execute(schema.getCreateTableSql());
            st.execute(schema.getCreateSessionIndexSql());
            String ttl = schema.getEnableTtlSql();
            if (ttl != null) {
                logger.info("Enabling CockroachDB row-level TTL on {}: {}", schema.getFullTableName(), ttl);
                st.execute(ttl);
            }
        } catch (SQLException e) {
            throw new CockroachDbChatMemoryStoreException("Failed to create chat memory table", e);
        }
    }

    public void disableTtl() {
        try (Connection conn = engine.getConnection();
                Statement st = conn.createStatement()) {
            st.execute(schema.getDisableTtlSql());
        } catch (SQLException e) {
            throw new CockroachDbChatMemoryStoreException("Failed to disable TTL", e);
        }
    }

    public static class Builder {
        private CockroachDbEngine engine;
        private CockroachDbChatMemorySchema schema;
        private CockroachDbChatMemorySchema.Builder schemaBuilder;

        public Builder engine(CockroachDbEngine engine) {
            this.engine = engine;
            return this;
        }

        public Builder schema(CockroachDbChatMemorySchema schema) {
            this.schema = schema;
            return this;
        }

        public Builder tableName(String tableName) {
            ensureSchemaBuilder().tableName(tableName);
            return this;
        }

        public Builder schemaName(String schemaName) {
            ensureSchemaBuilder().schemaName(schemaName);
            return this;
        }

        public Builder ttl(java.time.Duration ttl) {
            ensureSchemaBuilder().ttl(ttl);
            return this;
        }

        public Builder ttlJobCron(String cron) {
            ensureSchemaBuilder().ttlJobCron(cron);
            return this;
        }

        public Builder createTableIfNotExists(boolean v) {
            ensureSchemaBuilder().createTableIfNotExists(v);
            return this;
        }

        private CockroachDbChatMemorySchema.Builder ensureSchemaBuilder() {
            if (schemaBuilder == null) schemaBuilder = CockroachDbChatMemorySchema.builder();
            return schemaBuilder;
        }

        public CockroachDbChatMemoryStore build() {
            if (engine == null) {
                throw new IllegalArgumentException("CockroachDbEngine is required");
            }
            if (schema == null) {
                schema = (schemaBuilder == null ? CockroachDbChatMemorySchema.builder() : schemaBuilder).build();
            }
            return new CockroachDbChatMemoryStore(this);
        }
    }
}
