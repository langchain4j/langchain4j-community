package dev.langchain4j.community.store.memory.chat.sql;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class SQLChatMemoryStorePostgresIT extends SQLChatMemoryStoreIT {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine")).withReuse(true);

    @Override
    DataSource dataSource() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUser(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        return ds;
    }

    @Override
    SQLDialect dialect() {
        return new PostgreSQLDialect();
    }

    @Test
    void pg_upsert_sql_should_use_on_conflict_syntax() {
        String upsert = dialect().upsertSql("chat_memory", "memory_id", "content");
        assertThat(upsert).containsIgnoringCase("ON CONFLICT");
        assertThat(upsert).containsIgnoringCase("DO UPDATE SET");
        assertThat(upsert).containsIgnoringCase("EXCLUDED");
    }

    @Test
    void pg_create_table_sql_should_use_text_column() {
        String ddl = dialect().createTableSql("chat_memory", "memory_id", "content");
        assertThat(ddl).containsIgnoringCase("TEXT");
        assertThat(ddl).containsIgnoringCase("CREATE TABLE IF NOT EXISTS");
    }

    @Test
    void should_handle_concurrent_upserts_without_errors() throws InterruptedException {
        String memoryId = "concurrent-user";

        Thread t1 = new Thread(() -> store.updateMessages(memoryId, List.of()));
        Thread t2 = new Thread(() -> store.updateMessages(memoryId, List.of()));

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        assertThat(store.getMessages(memoryId)).isNotNull();
    }
}
