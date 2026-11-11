package dev.langchain4j.community.store.memory.chat.sql;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.message.UserMessage;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Isolated
public class SQLChatMemoryStoreH2IT extends SQLChatMemoryStoreIT {

    private static final JdbcDataSource DATA_SOURCE;

    static {
        DATA_SOURCE = new JdbcDataSource();
        DATA_SOURCE.setURL("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        DATA_SOURCE.setUser("sa");
        DATA_SOURCE.setPassword("");
    }

    @Override
    DataSource dataSource() {
        return DATA_SOURCE;
    }

    @Override
    SQLDialect dialect() {
        return new H2Dialect();
    }

    @Test
    void h2_create_table_sql_should_contain_merge_keyword() {
        String upsert = dialect().upsertSql("chat_memory", "memory_id", "content");
        assertThat(upsert).containsIgnoringCase("MERGE INTO");
        assertThat(upsert).containsIgnoringCase("KEY");
    }

    @Test
    void h2_create_table_sql_should_use_if_not_exists() {
        String ddl = dialect().createTableSql("chat_memory", "memory_id", "content");
        assertThat(ddl).containsIgnoringCase("CREATE TABLE IF NOT EXISTS");
        assertThat(ddl).containsIgnoringCase("PRIMARY KEY");
    }

    @Test
    void should_create_table_idempotently() {
        SQLChatMemoryStore store1 = SQLChatMemoryStore.builder()
                .dataSource(dataSource())
                .sqlDialect(dialect())
                .tableName("idempotent_table")
                .autoCreateTable(true)
                .build();

        SQLChatMemoryStore store2 = SQLChatMemoryStore.builder()
                .dataSource(dataSource())
                .sqlDialect(dialect())
                .tableName("idempotent_table")
                .autoCreateTable(true)
                .build();

        store1.updateMessages("u1", List.of());
        store2.updateMessages("u2", List.of());
    }

    @Test
    void should_accept_auto_create_false_when_table_already_exists() {

        SQLChatMemoryStore.builder()
                .dataSource(dataSource())
                .sqlDialect(dialect())
                .tableName("pre_existing_table")
                .memoryIdColumnName("memory_id")
                .contentColumnName("content")
                .autoCreateTable(true)
                .build();

        SQLChatMemoryStore store = SQLChatMemoryStore.builder()
                .dataSource(dataSource())
                .sqlDialect(dialect())
                .tableName("pre_existing_table")
                .autoCreateTable(false)
                .memoryIdColumnName("memory_id")
                .contentColumnName("content")
                .build();

        assertThat(store).isNotNull();
    }

    @ParameterizedTest
    @CsvSource({"en-US, false", "tr-TR, false", "az-AZ, false", "en-US, true", "tr-TR, true", "az-AZ, true"})
    void should_validate_existing_identifiers_independently_of_default_locale(String languageTag, boolean lowerCase)
            throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=" + lowerCase);
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE identity_memory (memory_id VARCHAR(255) PRIMARY KEY, content_info CLOB)");
        }

        Locale previous = Locale.getDefault();
        Locale previousDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        Locale previousFormat = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.forLanguageTag(languageTag));
            SQLChatMemoryStore store = SQLChatMemoryStore.builder()
                    .dataSource(dataSource)
                    .sqlDialect(dialect())
                    .tableName(lowerCase ? "IDENTITY_MEMORY" : "identity_memory")
                    .memoryIdColumnName(lowerCase ? "MEMORY_ID" : "memory_id")
                    .contentColumnName(lowerCase ? "CONTENT_INFO" : "content_info")
                    .autoCreateTable(false)
                    .build();

            store.updateMessages("session-1", List.of(UserMessage.from("hello")));
            assertThat(store.getMessages("session-1")).containsExactly(UserMessage.from("hello"));
            store.deleteMessages("session-1");
            assertThat(store.getMessages("session-1")).isEmpty();
        } finally {
            Locale.setDefault(previous);
            Locale.setDefault(Locale.Category.DISPLAY, previousDisplay);
            Locale.setDefault(Locale.Category.FORMAT, previousFormat);
            try (Connection connection = dataSource.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            }
        }
    }
}
