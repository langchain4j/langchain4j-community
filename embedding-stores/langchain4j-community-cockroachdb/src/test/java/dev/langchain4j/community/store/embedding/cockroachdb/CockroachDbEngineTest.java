package dev.langchain4j.community.store.embedding.cockroachdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Unit tests that do not require a running CockroachDB.
 */
class CockroachDbEngineTest {

    @Test
    void rewrites_cockroachdb_scheme_to_jdbc_postgresql() {
        assertThat(CockroachDbEngine.toJdbcUrl("cockroachdb://root@localhost:26257/defaultdb?sslmode=disable"))
                .isEqualTo("jdbc:postgresql://root@localhost:26257/defaultdb?sslmode=disable");
    }

    @Test
    void rewrites_cockroachdb_psycopg_scheme() {
        assertThat(CockroachDbEngine.toJdbcUrl("cockroachdb+psycopg://user:pw@host:26257/db"))
                .isEqualTo("jdbc:postgresql://user:pw@host:26257/db");
    }

    @Test
    void rewrites_postgresql_scheme() {
        assertThat(CockroachDbEngine.toJdbcUrl("postgresql://user@host:26257/db"))
                .isEqualTo("jdbc:postgresql://user@host:26257/db");
        assertThat(CockroachDbEngine.toJdbcUrl("postgres://user@host:26257/db"))
                .isEqualTo("jdbc:postgresql://user@host:26257/db");
    }

    @Test
    void passes_jdbc_url_through_unchanged() {
        String jdbc = "jdbc:postgresql://localhost:26257/defaultdb?sslmode=disable";
        assertThat(CockroachDbEngine.toJdbcUrl(jdbc)).isEqualTo(jdbc);
    }

    @Test
    void rejects_unsupported_scheme() {
        assertThatThrownBy(() -> CockroachDbEngine.toJdbcUrl("mysql://foo"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unsupported_scheme_error_does_not_contain_credentials() {
        assertThatThrownBy(() -> CockroachDbEngine.toJdbcUrl("mysql://user:secret-password@host:3306/db"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported connection-string scheme: mysql");

        assertThatThrownBy(() -> CockroachDbEngine.toJdbcUrl("user:secret-password@host:26257/db"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("secret-password");
    }

    @ParameterizedTest
    @CsvSource({
        "jdbc:postgresql://user:secret-password@host:26257/db?sslmode=verify-full, jdbc:postgresql://host:26257/db",
        "jdbc:postgresql://user:secret-password@host:26257/db, jdbc:postgresql://host:26257/db",
        "jdbc:postgresql://user@host:26257/db, jdbc:postgresql://host:26257/db",
        "jdbc:postgresql://host:26257/db?user=root&password=secret-password, jdbc:postgresql://host:26257/db",
        "jdbc:postgresql://host:26257/db?sslpassword=secret-password, jdbc:postgresql://host:26257/db",
        "jdbc:postgresql://user:secret/password@host:26257/db, jdbc:postgresql://host:26257/db",
        "jdbc:postgresql://user:secret?password@host:26257/db, ***",
        "jdbc:postgresql://host:26257/db?options=a@secret-password, ***",
        "jdbc:postgresql:db?password=secret-password, jdbc:postgresql:db",
        "'jdbc:postgresql://user:secret-password@host1:26257,host2:26257/db', 'jdbc:postgresql://host1:26257,host2:26257/db'"
    })
    void removes_credentials_from_url_for_logging(String jdbcUrl, String expected) {
        assertThat(CockroachDbEngine.withoutCredentials(jdbcUrl))
                .isEqualTo(expected)
                .doesNotContain("secret");
    }
}
