package dev.langchain4j.community.store.embedding.cockroachdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Unit tests that do not require a running CockroachDB. */
class CockroachDbEngineTest {

    @Test
    void rewrites_cockroachdb_scheme_to_jdbc_postgresql() {
        assertThat(CockroachDbEngine.toJdbcUrl(
                        "cockroachdb://root@localhost:26257/defaultdb?sslmode=disable"))
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
}
