package dev.langchain4j.store.embedding.sqlserver.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SQLServerTestsUtilTest {

    @Test
    void normalizeSqlServerHost_should_prefer_ipv4_for_localhost() {
        assertThat(SQLServerTestsUtil.normalizeSqlServerHost("localhost")).isEqualTo("127.0.0.1");
        assertThat(SQLServerTestsUtil.normalizeSqlServerHost("LOCALHOST")).isEqualTo("127.0.0.1");
        assertThat(SQLServerTestsUtil.normalizeSqlServerHost("::1")).isEqualTo("127.0.0.1");
        assertThat(SQLServerTestsUtil.normalizeSqlServerHost("0:0:0:0:0:0:0:1")).isEqualTo("127.0.0.1");
    }

    @Test
    void normalizeSqlServerHost_should_keep_non_localhost_hosts_unchanged() {
        assertThat(SQLServerTestsUtil.normalizeSqlServerHost("127.0.0.1")).isEqualTo("127.0.0.1");
        assertThat(SQLServerTestsUtil.normalizeSqlServerHost("192.168.0.10")).isEqualTo("192.168.0.10");
        assertThat(SQLServerTestsUtil.normalizeSqlServerHost("host.testcontainers.internal"))
                .isEqualTo("host.testcontainers.internal");
    }

    @Test
    void normalizeSqlServerHost_should_reject_null() {
        assertThatThrownBy(() -> SQLServerTestsUtil.normalizeSqlServerHost(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("host");
    }
}
