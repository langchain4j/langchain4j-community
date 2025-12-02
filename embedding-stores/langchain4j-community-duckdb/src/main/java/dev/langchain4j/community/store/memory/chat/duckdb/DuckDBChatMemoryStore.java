package dev.langchain4j.community.store.memory.chat.duckdb;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;
import static java.lang.String.format;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuckDBChatMemoryStore implements ChatMemoryStore {

    private static final Logger LOG = LoggerFactory.getLogger(DuckDBChatMemoryStore.class);

    private static final String CREATE_TABLE_TEMPLATE =
            """
                    create table if not exists %s (
                        id STRING PRIMARY KEY,
                        messages JSON NOT NULL,
                        create_at TIMESTAMPTZ NOT NULL default now(),
                        expire_at TIMESTAMPTZ
                        );
                    """;
    private static final String UPDATE_MESSAGE_TEMPLATE =
            """
                    insert or replace into %s (id, messages, create_at, expire_at) values (?, ?, now(), ?)
                    """;
    private static final String SELECT_MESSAGE_TEMPLATE =
            "select messages from %s where id = ? order by create_at desc";
    private static final String DELETE_MESSAGE_TEMPLATE = "delete from %s where id = ?";
    private static final String DELETE_EXPIRED_MESSAGE_TEMPLATE = "delete from %s where expire_at < now()";

    private final String tableName;
    private final DuckDBConnection duckDBConnection;
    private final Duration expirationDuration;

    public DuckDBChatMemoryStore(String filePath, String tableName, final Duration expirationDuration) {
        if (expirationDuration != null) {
            ensureTrue(
                    !expirationDuration.isNegative() && !expirationDuration.isZero(),
                    "Duration must be positive and greater than zero");
        }
        this.expirationDuration = expirationDuration;
        try {
            var dbUrl = filePath != null ? "jdbc:duckdb:" + filePath : "jdbc:duckdb:";
            this.tableName = getOrDefault(tableName, "chat_memory");
            this.duckDBConnection = (DuckDBConnection) DriverManager.getConnection(dbUrl);
            initTable();
        } catch (SQLException e) {
            throw new DuckDBChatMemoryException("Unable to load duckdb connection", e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String filePath;
        private String tableName;
        private Duration expirationDuration;

        /**
         * @param filePath File used to persist DuckDB database. If not specified, the database will be stored in-memory.
         * @return builder
         */
        public DuckDBChatMemoryStore.Builder filePath(String filePath) {
            this.filePath = filePath;
            return this;
        }

        /**
         * @param tableName The database table name to use. If not specified, "chat_memory" will be used
         * @return builder
         */
        public DuckDBChatMemoryStore.Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        /**
         * Convenience method to use an in-memory database directly.
         * @param tableName The database table name to use. If not specified, "chat_memory" will be used
         * @return builder
         */
        public DuckDBChatMemoryStore.Builder inMemory(String tableName) {
            this.tableName = tableName;
            return filePath(null);
        }

        /**
         * @param expirationDuration Duration after which the memory will be expired
         * @return builder
         */
        public DuckDBChatMemoryStore.Builder expirationDuration(Duration expirationDuration) {
            this.expirationDuration = expirationDuration;
            return this;
        }

        public DuckDBChatMemoryStore build() {
            return new DuckDBChatMemoryStore(filePath, tableName, expirationDuration);
        }
    }

    public static DuckDBChatMemoryStore inMemory() {
        return new DuckDBChatMemoryStore(null, null, Duration.parse("PT1H"));
    }

    @Override
    public List<ChatMessage> getMessages(final Object memoryId) {
        var memoryIdStr = memoryIdToString(memoryId);
        var query = format(SELECT_MESSAGE_TEMPLATE, tableName);
        try (var connection = duckDBConnection.duplicate();
                var statement = connection.prepareStatement(query)) {
            LOG.debug(query);
            statement.setString(1, memoryIdStr);
            var results = statement.executeQuery();
            while (results.next()) {
                var message = results.getString(1);
                if (message != null && !message.trim().isEmpty()) {
                    return ChatMessageDeserializer.messagesFromJson(message);
                }
            }
            return List.of();
        } catch (SQLException e) {
            throw new DuckDBChatMemoryException(
                    format("Unable to retrieve memory in DuckDB. memoryId=%s", memoryId), e);
        }
    }

    @Override
    public void updateMessages(final Object memoryId, final List<ChatMessage> messages) {
        var memoryIdStr = memoryIdToString(memoryId);
        var updateQuery = format(UPDATE_MESSAGE_TEMPLATE, tableName);
        Timestamp expirationParam = null;
        if (expirationDuration != null && !expirationDuration.isZero() && !expirationDuration.isNegative()) {
            expirationParam = Timestamp.from(Instant.now().plus(expirationDuration));
        }
        List<ChatMessage> validatedMessages = ensureNotEmpty(messages, "messages");
        String messagesJson = ChatMessageSerializer.messagesToJson(validatedMessages);
        try (var connection = duckDBConnection.duplicate();
                var statement = connection.prepareStatement(updateQuery)) {
            LOG.debug(updateQuery);
            statement.setString(1, memoryIdStr);
            statement.setString(2, messagesJson);
            statement.setTimestamp(3, expirationParam);
            statement.execute();
        } catch (SQLException e) {
            throw new DuckDBChatMemoryException(
                    format("Unable to update memory in DuckDB. memoryId=%s", memoryIdStr), e);
        }
    }

    @Override
    public void deleteMessages(final Object memoryId) {
        var memoryIdStr = memoryIdToString(memoryId);
        var query = format(DELETE_MESSAGE_TEMPLATE, tableName);
        try (var connection = duckDBConnection.duplicate();
                var statement = connection.prepareStatement(query)) {
            LOG.debug(query);
            statement.setString(1, memoryIdStr);
            statement.execute();
        } catch (SQLException e) {
            throw new DuckDBChatMemoryException(
                    format("Unable to delete memory in DuckDB. memoryId=%s", memoryIdStr), e);
        }
    }

    public void cleanExpiredMessage() {
        var query = format(DELETE_EXPIRED_MESSAGE_TEMPLATE, tableName);
        try (var connection = duckDBConnection.duplicate();
                var statement = connection.createStatement()) {
            LOG.debug(query);
            statement.execute(query);
        } catch (SQLException e) {
            throw new DuckDBChatMemoryException("Unable to clean expired message in DuckDB.", e);
        }
    }

    private void initTable() {
        var sql = format(CREATE_TABLE_TEMPLATE, tableName);
        try (var connection = duckDBConnection.duplicate();
                var statement = connection.createStatement()) {
            LOG.debug(sql);
            statement.execute(sql);
        } catch (SQLException e) {
            throw new DuckDBChatMemoryException(format("Failed to init duckDB table:  '%s'", sql), e);
        }
    }

    private static String memoryIdToString(Object memoryId) {
        ensureNotNull(memoryId, "memoryId");
        ensureNotBlank(memoryId.toString(), "memoryId");
        return memoryId.toString();
    }
}
