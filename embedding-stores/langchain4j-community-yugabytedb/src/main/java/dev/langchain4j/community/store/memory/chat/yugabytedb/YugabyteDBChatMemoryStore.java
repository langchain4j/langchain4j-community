package dev.langchain4j.community.store.memory.chat.yugabytedb;

import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.community.store.embedding.yugabytedb.YugabyteDBEngine;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link ChatMemoryStore} that stores chat messages in YugabyteDB.
 * Uses YugabyteDBEngine to connect to YugabyteDB and manage message persistence.
 * <p>
 * Messages are stored as JSON in a dedicated table with optional TTL (time-to-live)
 * for automatic message expiration. The implementation supports custom table and column names
 * for flexibility in different deployment scenarios.
 * </p>
 * <p>
 * Table schema:
 * </p>
 * <pre>
 * CREATE TABLE chat_memory (
 *     id TEXT PRIMARY KEY,
 *     messages JSONB NOT NULL,
 *     created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
 *     expires_at TIMESTAMP WITH TIME ZONE
 * );
 * </pre>
 */
public class YugabyteDBChatMemoryStore implements ChatMemoryStore {

    private static final Logger logger = LoggerFactory.getLogger(YugabyteDBChatMemoryStore.class);

    /**
     * YugabyteDB engine for database operations.
     */
    private final YugabyteDBEngine engine;

    /**
     * Schema configuration for the chat memory table.
     */
    private final YugabyteDBChatMemorySchema schema;

    /**
     * Time-to-live duration for chat messages.
     * Messages older than this duration will be eligible for cleanup.
     * A null value means messages will not expire.
     */
    private final Duration ttl;

    /**
     * Constructs a new YugabyteDB chat memory store.
     *
     * @param builder the builder containing configuration
     */
    public YugabyteDBChatMemoryStore(Builder builder) {
        this.engine = ensureNotNull(builder.engine, "engine");
        this.schema = ensureNotNull(builder.schema, "schema");
        this.ttl = builder.ttl;

        if (schema.isCreateTableIfNotExists()) {
            createTableIfNotExists();
        }
    }

    /**
     * Retrieves all chat messages associated with the given memory ID.
     *
     * @param memoryId The identifier for the memory to retrieve
     * @return List of chat messages or an empty list if no messages found
     * @throws YugabyteDBChatMemoryStoreException If the operation fails
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String memoryIdString = toMemoryIdString(memoryId);

        String sql = String.format(
                "SELECT %s FROM %s WHERE %s = ?",
                schema.getMessagesColumn(), schema.getFullTableName(), schema.getIdColumn());

        try (Connection connection = engine.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, memoryIdString);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String messagesJson = resultSet.getString(schema.getMessagesColumn());
                    if (messagesJson != null && !messagesJson.trim().isEmpty()) {
                        return ChatMessageDeserializer.messagesFromJson(messagesJson);
                    }
                }
                return new ArrayList<>();
            }
        } catch (Exception e) {
            throw new YugabyteDBChatMemoryStoreException(
                    "Failed to retrieve messages for memoryId: " + memoryIdString, e);
        }
    }

    /**
     * Updates the messages associated with the given memory ID.
     * If TTL is set, the messages will automatically expire after the specified duration.
     *
     * @param memoryId The identifier for the memory to update
     * @param messages The list of messages to store
     * @throws YugabyteDBChatMemoryStoreException If the operation fails
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String memoryIdString = toMemoryIdString(memoryId);
        List<ChatMessage> validatedMessages = ensureNotEmpty(messages, "messages");

        String messagesJson = ChatMessageSerializer.messagesToJson(validatedMessages);

        String sql;
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            sql = String.format(
                    "INSERT INTO %s (%s, %s, %s, %s) VALUES (?, ?::jsonb, NOW(), ?) "
                            + "ON CONFLICT (%s) DO UPDATE SET %s = EXCLUDED.%s, %s = EXCLUDED.%s, %s = EXCLUDED.%s",
                    schema.getFullTableName(),
                    schema.getIdColumn(),
                    schema.getMessagesColumn(),
                    schema.getCreatedAtColumn(),
                    schema.getExpiresAtColumn(),
                    schema.getIdColumn(),
                    schema.getMessagesColumn(),
                    schema.getMessagesColumn(),
                    schema.getCreatedAtColumn(),
                    schema.getCreatedAtColumn(),
                    schema.getExpiresAtColumn(),
                    schema.getExpiresAtColumn());
        } else {
            sql = String.format(
                    "INSERT INTO %s (%s, %s, %s) VALUES (?, ?::jsonb, NOW()) "
                            + "ON CONFLICT (%s) DO UPDATE SET %s = EXCLUDED.%s, %s = EXCLUDED.%s",
                    schema.getFullTableName(),
                    schema.getIdColumn(),
                    schema.getMessagesColumn(),
                    schema.getCreatedAtColumn(),
                    schema.getIdColumn(),
                    schema.getMessagesColumn(),
                    schema.getMessagesColumn(),
                    schema.getCreatedAtColumn(),
                    schema.getCreatedAtColumn());
        }

        try (Connection connection = engine.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, memoryIdString);
            statement.setString(2, messagesJson);

            if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
                Timestamp expiresAt = Timestamp.from(Instant.now().plus(ttl));
                statement.setTimestamp(3, expiresAt);
            }

            int rowsAffected = statement.executeUpdate();
            if (rowsAffected == 0) {
                throw new YugabyteDBChatMemoryStoreException(
                        "Failed to update messages for memoryId: " + memoryIdString);
            }

            logger.debug("Updated {} messages for memoryId: {}", validatedMessages.size(), memoryIdString);
        } catch (SQLException e) {
            throw new YugabyteDBChatMemoryStoreException(
                    "Failed to update messages for memoryId: " + memoryIdString, e);
        } catch (Exception e) {
            throw new YugabyteDBChatMemoryStoreException(
                    "Failed to update messages for memoryId: " + memoryIdString, e);
        }
    }

    /**
     * Deletes all messages associated with the given memory ID.
     *
     * @param memoryId The identifier for the memory to delete
     * @throws YugabyteDBChatMemoryStoreException If the operation fails
     */
    @Override
    public void deleteMessages(Object memoryId) {
        String memoryIdString = toMemoryIdString(memoryId);

        String sql = String.format("DELETE FROM %s WHERE %s = ?", schema.getFullTableName(), schema.getIdColumn());

        try (Connection connection = engine.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, memoryIdString);
            int rowsAffected = statement.executeUpdate();

            logger.debug("Deleted {} rows for memoryId: {}", rowsAffected, memoryIdString);
        } catch (SQLException e) {
            throw new YugabyteDBChatMemoryStoreException(
                    "Failed to delete messages for memoryId: " + memoryIdString, e);
        } catch (Exception e) {
            throw new YugabyteDBChatMemoryStoreException(
                    "Failed to delete messages for memoryId: " + memoryIdString, e);
        }
    }

    /**
     * Cleans up expired messages based on the configured TTL.
     * This method can be called periodically to remove old messages.
     *
     * @return The number of expired message records that were deleted
     * @throws YugabyteDBChatMemoryStoreException If the cleanup operation fails
     */
    public int cleanupExpiredMessages() {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            logger.debug("TTL is not configured, skipping cleanup");
            return 0;
        }

        String sql = String.format(
                "DELETE FROM %s WHERE %s IS NOT NULL AND %s < NOW()",
                schema.getFullTableName(), schema.getExpiresAtColumn(), schema.getExpiresAtColumn());

        try (Connection connection = engine.getConnection();
                Statement statement = connection.createStatement()) {

            int deletedCount = statement.executeUpdate(sql);
            if (deletedCount > 0) {
                logger.info("Cleaned up {} expired chat memory records", deletedCount);
            }
            return deletedCount;
        } catch (SQLException e) {
            throw new YugabyteDBChatMemoryStoreException("Failed to cleanup expired messages", e);
        } catch (Exception e) {
            throw new YugabyteDBChatMemoryStoreException("Failed to cleanup expired messages", e);
        }
    }

    /**
     * Converts a memory ID object to a string representation.
     *
     * @param memoryId The memory ID to convert
     * @return String representation of the memory ID
     * @throws IllegalArgumentException If memoryId is null or empty
     */
    private String toMemoryIdString(Object memoryId) {
        if (memoryId == null || memoryId.toString().trim().isEmpty()) {
            throw new IllegalArgumentException("memoryId cannot be null or empty");
        }
        return memoryId.toString();
    }

    /**
     * Creates the chat memory table if it doesn't exist.
     *
     * @throws YugabyteDBChatMemoryStoreException If table creation fails
     */
    private void createTableIfNotExists() {
        try (Connection connection = engine.getConnection();
                Statement statement = connection.createStatement()) {

            statement.execute(schema.getCreateTableSql());
            statement.execute(schema.getCreateIndexSql());

            logger.debug("Ensured chat memory table exists: {}", schema.getFullTableName());
        } catch (SQLException e) {
            throw new YugabyteDBChatMemoryStoreException("Failed to create chat memory table", e);
        } catch (Exception e) {
            throw new YugabyteDBChatMemoryStoreException("Failed to create chat memory table", e);
        }
    }

    /**
     * Creates a new builder instance for constructing a YugabyteDBChatMemoryStore.
     *
     * @return A new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating YugabyteDBChatMemoryStore instances with fluent API.
     */
    public static class Builder {
        private YugabyteDBEngine engine;
        private YugabyteDBChatMemorySchema schema;
        private Duration ttl;

        // Schema builder for convenience methods
        private YugabyteDBChatMemorySchema.Builder schemaBuilder;

        /**
         * Sets the YugabyteDB engine.
         *
         * @param engine The YugabyteDB engine for database operations
         * @return This builder for method chaining
         */
        public Builder engine(YugabyteDBEngine engine) {
            this.engine = engine;
            return this;
        }

        /**
         * Sets the schema configuration.
         *
         * @param schema The schema configuration for the chat memory table
         * @return This builder for method chaining
         */
        public Builder schema(YugabyteDBChatMemorySchema schema) {
            this.schema = schema;
            return this;
        }

        /**
         * Sets the Time-To-Live (TTL) duration for chat messages.
         * Messages older than this duration will be eligible for cleanup.
         *
         * @param ttl The TTL duration. A null value means messages will not expire.
         * @return This builder for method chaining
         */
        public Builder ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        // Convenience methods for backward compatibility and easier configuration
        public Builder tableName(String tableName) {
            ensureSchemaBuilder().tableName(tableName);
            return this;
        }

        public Builder schemaName(String schemaName) {
            ensureSchemaBuilder().schemaName(schemaName);
            return this;
        }

        public Builder idColumn(String idColumn) {
            ensureSchemaBuilder().idColumn(idColumn);
            return this;
        }

        public Builder messagesColumn(String messagesColumn) {
            ensureSchemaBuilder().messagesColumn(messagesColumn);
            return this;
        }

        public Builder createdAtColumn(String createdAtColumn) {
            ensureSchemaBuilder().createdAtColumn(createdAtColumn);
            return this;
        }

        public Builder expiresAtColumn(String expiresAtColumn) {
            ensureSchemaBuilder().expiresAtColumn(expiresAtColumn);
            return this;
        }

        public Builder createTableIfNotExists(boolean createTableIfNotExists) {
            ensureSchemaBuilder().createTableIfNotExists(createTableIfNotExists);
            return this;
        }

        private YugabyteDBChatMemorySchema.Builder ensureSchemaBuilder() {
            if (schemaBuilder == null) {
                schemaBuilder = YugabyteDBChatMemorySchema.builder();
            }
            return schemaBuilder;
        }

        /**
         * Builds a new YugabyteDBChatMemoryStore instance with the configured parameters.
         *
         * @return A new YugabyteDBChatMemoryStore instance
         * @throws IllegalArgumentException If required parameters are missing
         */
        public YugabyteDBChatMemoryStore build() {
            if (engine == null) {
                throw new IllegalArgumentException("YugabyteDBEngine is required");
            }

            if (schema == null) {
                if (schemaBuilder == null) {
                    schemaBuilder = YugabyteDBChatMemorySchema.builder();
                }
                schema = schemaBuilder.build();
            }

            return new YugabyteDBChatMemoryStore(this);
        }
    }
}
