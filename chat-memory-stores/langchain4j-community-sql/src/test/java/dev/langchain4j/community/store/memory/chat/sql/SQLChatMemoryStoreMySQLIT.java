package dev.langchain4j.community.store.memory.chat.sql;

import static org.assertj.core.api.Assertions.assertThat;

import com.mysql.cj.jdbc.MysqlDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class SQLChatMemoryStoreMySQLIT extends SQLChatMemoryStoreIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>(DockerImageName.parse("mysql:8.0")).withReuse(true);

    @Override
    DataSource dataSource() {
        MysqlDataSource ds = new MysqlDataSource();
        ds.setUrl(mysql.getJdbcUrl());
        ds.setUser(mysql.getUsername());
        ds.setPassword(mysql.getPassword());
        return ds;
    }

    @Override
    SQLDialect dialect() {
        return new MySQLDialect();
    }

    @Test
    void mysql_upsert_sql_should_use_on_duplicate_key_syntax() {
        String upsert = dialect().upsertSql("chat_memory", "memory_id", "content");
        assertThat(upsert).containsIgnoringCase("ON DUPLICATE KEY UPDATE");
        assertThat(upsert).containsIgnoringCase("VALUES(");
    }

    @Test
    void mysql_create_table_sql_should_use_longtext_column() {
        String ddl = dialect().createTableSql("chat_memory", "memory_id", "content");
        assertThat(ddl).containsIgnoringCase("LONGTEXT");
        assertThat(ddl).containsIgnoringCase("CREATE TABLE IF NOT EXISTS");
    }
}
