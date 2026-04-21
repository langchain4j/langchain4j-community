package dev.langchain4j.community.store.memory.chat.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

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
}
