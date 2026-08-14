package dev.langchain4j.community.code.docker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DockerExecutionConfigTest {

    @Test
    void should_create_config_with_defaults() {
        DockerExecutionConfig config = DockerExecutionConfig.builder().build();

        assertThat(config.memoryLimit()).isEqualTo("256m");
        assertThat(config.timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(config.networkDisabled()).isTrue();
        assertThat(config.readOnlyRootfs()).isFalse();
        assertThat(config.capDrop()).containsExactly("ALL");
        assertThat(config.workingDir()).isEqualTo("/app");
        assertThat(config.maxOutputSizeBytes()).isEqualTo(1024 * 1024);
        assertThat(config.cpuShares()).isEqualTo(1024);
    }

    @Test
    void should_create_config_with_custom_memory_limit() {
        DockerExecutionConfig config =
                DockerExecutionConfig.builder().memoryLimit("512m").build();

        assertThat(config.memoryLimit()).isEqualTo("512m");
        assertThat(config.memoryLimitBytes()).isEqualTo(512L * 1024L * 1024L);
    }

    @ParameterizedTest
    @CsvSource({"256m, 268435456", "1g, 1073741824", "512k, 524288", "100mb, 104857600", "2gb, 2147483648", "1024, 1024"
    })
    void should_parse_memory_string_correctly(String memoryString, long expectedBytes) {
        DockerExecutionConfig config =
                DockerExecutionConfig.builder().memoryLimit(memoryString).build();

        assertThat(config.memoryLimitBytes()).isEqualTo(expectedBytes);
    }

    @Test
    void should_create_config_with_custom_timeout() {
        DockerExecutionConfig config =
                DockerExecutionConfig.builder().timeout(Duration.ofMinutes(2)).build();

        assertThat(config.timeout()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void should_create_config_with_network_enabled() {
        DockerExecutionConfig config =
                DockerExecutionConfig.builder().networkDisabled(false).build();

        assertThat(config.networkDisabled()).isFalse();
    }

    @Test
    void should_create_config_with_readonly_rootfs() {
        DockerExecutionConfig config =
                DockerExecutionConfig.builder().readOnlyRootfs(true).build();

        assertThat(config.readOnlyRootfs()).isTrue();
    }

    @Test
    void should_create_config_with_custom_capabilities() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .capDrop(List.of("NET_RAW", "MKNOD"))
                .build();

        assertThat(config.capDrop()).containsExactly("NET_RAW", "MKNOD");
    }

    @Test
    void should_create_config_with_custom_user() {
        DockerExecutionConfig config =
                DockerExecutionConfig.builder().user("1000:1000").build();

        assertThat(config.user()).isEqualTo("1000:1000");
    }

    @Test
    void should_create_config_with_custom_working_dir() {
        DockerExecutionConfig config =
                DockerExecutionConfig.builder().workingDir("/code").build();

        assertThat(config.workingDir()).isEqualTo("/code");
    }

    @Test
    void should_create_config_with_environment_variables() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .environmentVariables(Map.of("VAR1", "value1", "VAR2", "value2"))
                .build();

        assertThat(config.environmentVariables())
                .containsEntry("VAR1", "value1")
                .containsEntry("VAR2", "value2");
    }

    @Test
    void should_add_single_environment_variable() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .addEnvironmentVariable("MY_VAR", "my_value")
                .build();

        assertThat(config.environmentVariables()).containsEntry("MY_VAR", "my_value");
    }

    @Test
    void should_create_config_with_docker_host() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .dockerHost("tcp://localhost:2375")
                .build();

        assertThat(config.dockerHost()).isEqualTo("tcp://localhost:2375");
    }

    @Test
    void should_create_config_with_tls_verify() {
        DockerExecutionConfig config =
                DockerExecutionConfig.builder().tlsVerify(true).build();

        assertThat(config.tlsVerify()).isTrue();
    }

    @Test
    void should_create_config_with_cpu_settings() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .cpuShares(512)
                .cpuPeriodMicros(100000L)
                .cpuQuotaMicros(50000L)
                .build();

        assertThat(config.cpuShares()).isEqualTo(512);
        assertThat(config.cpuPeriodMicros()).isEqualTo(100000L);
        assertThat(config.cpuQuotaMicros()).isEqualTo(50000L);
    }

    @Test
    void should_create_config_with_memory_swap() {
        DockerExecutionConfig config =
                DockerExecutionConfig.builder().memorySwapBytes(-1L).build();

        assertThat(config.memorySwapBytes()).isEqualTo(-1L);
    }

    @Test
    void should_create_config_with_max_output_size() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .maxOutputSizeBytes(2 * 1024 * 1024)
                .build();

        assertThat(config.maxOutputSizeBytes()).isEqualTo(2 * 1024 * 1024);
    }

    @Test
    void should_reject_null_timeout() {
        assertThatThrownBy(() -> DockerExecutionConfig.builder().timeout(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_blank_memory_limit() {
        assertThatThrownBy(() -> DockerExecutionConfig.builder().memoryLimit(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_blank_working_dir() {
        assertThatThrownBy(() -> DockerExecutionConfig.builder().workingDir(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_null_cap_drop() {
        assertThatThrownBy(() -> DockerExecutionConfig.builder().capDrop(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_zero_max_output_size() {
        assertThatThrownBy(() -> DockerExecutionConfig.builder().maxOutputSizeBytes(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_negative_max_output_size() {
        assertThatThrownBy(() -> DockerExecutionConfig.builder().maxOutputSizeBytes(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_return_immutable_cap_drop_list() {
        DockerExecutionConfig config = DockerExecutionConfig.builder().build();

        assertThatThrownBy(() -> config.capDrop().add("NEW_CAP")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_return_immutable_environment_variables_map() {
        DockerExecutionConfig config = DockerExecutionConfig.builder().build();

        assertThatThrownBy(() -> config.environmentVariables().put("key", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void should_have_meaningful_to_string() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .memoryLimit("512m")
                .timeout(Duration.ofSeconds(60))
                .build();

        String toString = config.toString();

        assertThat(toString)
                .contains("memoryLimit='512m'")
                .contains("timeout=PT1M")
                .contains("networkDisabled=true");
    }

    @Test
    void should_handle_null_memory_limit_bytes() {
        DockerExecutionConfig config = DockerExecutionConfig.builder().build();

        // memoryLimit is set by default, but let's verify it works
        assertThat(config.memoryLimitBytes()).isNotNull();
    }

    @Test
    void should_create_production_safe_config() {
        // Verify that default config is production-safe
        DockerExecutionConfig config = DockerExecutionConfig.builder().build();

        // Network should be disabled
        assertThat(config.networkDisabled()).isTrue();

        // All capabilities should be dropped
        assertThat(config.capDrop()).contains("ALL");

        // Memory should be limited
        assertThat(config.memoryLimitBytes()).isLessThan(1024L * 1024L * 1024L); // < 1GB

        // Timeout should be set
        assertThat(config.timeout()).isNotNull();
        assertThat(config.timeout().getSeconds()).isLessThanOrEqualTo(300); // <= 5 min
    }
}
