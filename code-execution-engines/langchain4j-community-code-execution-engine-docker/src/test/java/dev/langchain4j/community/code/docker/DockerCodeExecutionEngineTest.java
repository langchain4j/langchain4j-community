package dev.langchain4j.community.code.docker;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DockerCodeExecutionEngine}.
 * <p>
 * These tests verify the engine's configuration and error handling.
 * Integration tests with actual Docker execution are in DockerCodeExecutionEngineIT.
 * </p>
 */
class DockerCodeExecutionEngineTest {

    @Test
    void should_create_engine_with_default_configuration() {
        DockerExecutionConfig config = DockerExecutionConfig.builder().build();
        DockerCodeExecutionEngine engine = new DockerCodeExecutionEngine(config);

        assertThat(engine).isNotNull();
        assertThat(engine.getConfig()).isNotNull();
        assertThat(engine.getConfig().memoryLimit()).isEqualTo("256m");
        assertThat(engine.getConfig().timeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(engine.getConfig().networkDisabled()).isTrue();
        assertThat(engine.getConfig().capDrop()).containsExactly("ALL");

        engine.close();
    }

    @Test
    void should_create_engine_with_static_factory() {
        DockerCodeExecutionEngine engine = DockerCodeExecutionEngine.withDefaultConfig();

        assertThat(engine).isNotNull();
        assertThat(engine.getConfig().memoryLimit()).isEqualTo("256m");
        assertThat(engine.getConfig().timeout()).isEqualTo(Duration.ofSeconds(30));

        engine.close();
    }

    @Test
    void should_create_engine_with_custom_memory_limit() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .memoryLimit("512m")
                .build();
        DockerCodeExecutionEngine engine = new DockerCodeExecutionEngine(config);

        assertThat(engine.getConfig().memoryLimit()).isEqualTo("512m");
        assertThat(engine.getConfig().memoryLimitBytes()).isEqualTo(512L * 1024 * 1024);

        engine.close();
    }

    @Test
    void should_create_engine_with_custom_timeout() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .timeout(Duration.ofMinutes(2))
                .build();
        DockerCodeExecutionEngine engine = new DockerCodeExecutionEngine(config);

        assertThat(engine.getConfig().timeout()).isEqualTo(Duration.ofMinutes(2));

        engine.close();
    }

    @Test
    void should_create_engine_with_network_enabled() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .networkDisabled(false)
                .build();
        DockerCodeExecutionEngine engine = new DockerCodeExecutionEngine(config);

        assertThat(engine.getConfig().networkDisabled()).isFalse();

        engine.close();
    }

    @Test
    void should_create_engine_with_custom_capabilities() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .capDrop(List.of("NET_RAW", "MKNOD"))
                .build();
        DockerCodeExecutionEngine engine = new DockerCodeExecutionEngine(config);

        assertThat(engine.getConfig().capDrop()).containsExactly("NET_RAW", "MKNOD");

        engine.close();
    }

    @Test
    void should_create_engine_with_custom_user() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .user("1000:1000")
                .build();
        DockerCodeExecutionEngine engine = new DockerCodeExecutionEngine(config);

        assertThat(engine.getConfig().user()).isEqualTo("1000:1000");

        engine.close();
    }

    @Test
    void should_create_engine_with_read_only_rootfs() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .readOnlyRootfs(true)
                .build();
        DockerCodeExecutionEngine engine = new DockerCodeExecutionEngine(config);

        assertThat(engine.getConfig().readOnlyRootfs()).isTrue();

        engine.close();
    }

    @Test
    void should_create_engine_with_custom_working_dir() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .workingDir("/workspace")
                .build();
        DockerCodeExecutionEngine engine = new DockerCodeExecutionEngine(config);

        assertThat(engine.getConfig().workingDir()).isEqualTo("/workspace");

        engine.close();
    }

    @Test
    void should_create_engine_with_environment_variables() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .environmentVariables(Map.of("KEY1", "value1", "KEY2", "value2"))
                .build();
        DockerCodeExecutionEngine engine = new DockerCodeExecutionEngine(config);

        assertThat(engine.getConfig().environmentVariables())
                .containsEntry("KEY1", "value1")
                .containsEntry("KEY2", "value2");

        engine.close();
    }

    @Test
    void should_create_engine_with_added_environment_variable() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .addEnvironmentVariable("MY_VAR", "my_value")
                .build();
        DockerCodeExecutionEngine engine = new DockerCodeExecutionEngine(config);

        assertThat(engine.getConfig().environmentVariables())
                .containsEntry("MY_VAR", "my_value");

        engine.close();
    }

    @Test
    void should_create_engine_with_custom_output_size() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .maxOutputSizeBytes(2 * 1024 * 1024)
                .build();
        DockerCodeExecutionEngine engine = new DockerCodeExecutionEngine(config);

        assertThat(engine.getConfig().maxOutputSizeBytes()).isEqualTo(2 * 1024 * 1024);

        engine.close();
    }

    @Test
    void should_create_engine_with_cpu_shares() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .cpuShares(512)
                .build();
        DockerCodeExecutionEngine engine = new DockerCodeExecutionEngine(config);

        assertThat(engine.getConfig().cpuShares()).isEqualTo(512);

        engine.close();
    }

    @Test
    void should_create_engine_with_custom_docker_host() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .dockerHost("tcp://localhost:2375")
                .build();
        DockerCodeExecutionEngine engine = new DockerCodeExecutionEngine(config);

        assertThat(engine.getConfig().dockerHost()).isEqualTo("tcp://localhost:2375");

        engine.close();
    }

    @Test
    void should_create_config_with_tls_verify() {
        // Note: We only test the config builder, not the engine,
        // because enabling TLS without certs causes Docker client creation to fail
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .tlsVerify(true)
                .build();

        assertThat(config.tlsVerify()).isTrue();
    }

    @Test
    void should_create_fully_configured_engine() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .memoryLimit("1g")
                .timeout(Duration.ofMinutes(5))
                .networkDisabled(true)
                .maxOutputSizeBytes(5 * 1024 * 1024)
                .capDrop(List.of("ALL"))
                .user("nobody")
                .readOnlyRootfs(false)
                .workingDir("/code")
                .cpuShares(2048)
                .addEnvironmentVariable("DEBUG", "true")
                .build();
        DockerCodeExecutionEngine engine = new DockerCodeExecutionEngine(config);

        DockerExecutionConfig engineConfig = engine.getConfig();
        assertThat(engineConfig.memoryLimit()).isEqualTo("1g");
        assertThat(engineConfig.timeout()).isEqualTo(Duration.ofMinutes(5));
        assertThat(engineConfig.networkDisabled()).isTrue();
        assertThat(engineConfig.maxOutputSizeBytes()).isEqualTo(5 * 1024 * 1024);
        assertThat(engineConfig.capDrop()).containsExactly("ALL");
        assertThat(engineConfig.user()).isEqualTo("nobody");
        assertThat(engineConfig.readOnlyRootfs()).isFalse();
        assertThat(engineConfig.workingDir()).isEqualTo("/code");
        assertThat(engineConfig.cpuShares()).isEqualTo(2048);
        assertThat(engineConfig.environmentVariables()).containsEntry("DEBUG", "true");

        engine.close();
    }

    // Parameter validation tests for execute()

    @Test
    void should_reject_null_image() {
        DockerCodeExecutionEngine engine = DockerCodeExecutionEngine.withDefaultConfig();

        assertThatThrownBy(() -> engine.execute(null, ".py", "print(1)", "python"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("image");

        engine.close();
    }

    @Test
    void should_reject_blank_image() {
        DockerCodeExecutionEngine engine = DockerCodeExecutionEngine.withDefaultConfig();

        assertThatThrownBy(() -> engine.execute("", ".py", "print(1)", "python"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("image");

        assertThatThrownBy(() -> engine.execute("   ", ".py", "print(1)", "python"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("image");

        engine.close();
    }

    @Test
    void should_reject_null_file_extension() {
        DockerCodeExecutionEngine engine = DockerCodeExecutionEngine.withDefaultConfig();

        assertThatThrownBy(() -> engine.execute("python:3.12-slim", null, "print(1)", "python"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fileExtension");

        engine.close();
    }

    @Test
    void should_reject_blank_file_extension() {
        DockerCodeExecutionEngine engine = DockerCodeExecutionEngine.withDefaultConfig();

        assertThatThrownBy(() -> engine.execute("python:3.12-slim", "", "print(1)", "python"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fileExtension");

        engine.close();
    }

    @Test
    void should_reject_null_code() {
        DockerCodeExecutionEngine engine = DockerCodeExecutionEngine.withDefaultConfig();

        assertThatThrownBy(() -> engine.execute("python:3.12-slim", ".py", null, "python"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");

        engine.close();
    }

    @Test
    void should_reject_blank_code() {
        DockerCodeExecutionEngine engine = DockerCodeExecutionEngine.withDefaultConfig();

        assertThatThrownBy(() -> engine.execute("python:3.12-slim", ".py", "", "python"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");

        engine.close();
    }

    @Test
    void should_reject_null_command() {
        DockerCodeExecutionEngine engine = DockerCodeExecutionEngine.withDefaultConfig();

        assertThatThrownBy(() -> engine.execute("python:3.12-slim", ".py", "print(1)", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command");

        engine.close();
    }

    @Test
    void should_reject_blank_command() {
        DockerCodeExecutionEngine engine = DockerCodeExecutionEngine.withDefaultConfig();

        assertThatThrownBy(() -> engine.execute("python:3.12-slim", ".py", "print(1)", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("command");

        engine.close();
    }

    // Constructor validation tests

    @Test
    void should_reject_null_config() {
        assertThatThrownBy(() -> new DockerCodeExecutionEngine(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config");
    }

    // Config builder validation tests (testing DockerExecutionConfig.Builder)

    @Test
    void should_reject_null_timeout() {
        assertThatThrownBy(() -> DockerExecutionConfig.builder()
                .timeout(null)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void should_reject_blank_memory_limit() {
        assertThatThrownBy(() -> DockerExecutionConfig.builder()
                .memoryLimit("")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memoryLimit");
    }

    @Test
    void should_reject_blank_working_dir() {
        assertThatThrownBy(() -> DockerExecutionConfig.builder()
                .workingDir("")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workingDir");
    }

    @Test
    void should_reject_zero_output_size() {
        assertThatThrownBy(() -> DockerExecutionConfig.builder()
                .maxOutputSizeBytes(0)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxOutputSizeBytes");
    }

    @Test
    void should_reject_negative_output_size() {
        assertThatThrownBy(() -> DockerExecutionConfig.builder()
                .maxOutputSizeBytes(-1)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxOutputSizeBytes");
    }

    @Test
    void should_reject_null_cap_drop() {
        assertThatThrownBy(() -> DockerExecutionConfig.builder()
                .capDrop(null)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capDrop");
    }

    @Test
    void should_reject_null_environment_variables() {
        assertThatThrownBy(() -> DockerExecutionConfig.builder()
                .environmentVariables(null)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("environmentVariables");
    }
}
