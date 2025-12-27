package dev.langchain4j.community.code.docker;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

/**
 * Unit tests for registry authentication support in {@link DockerExecutionConfig}.
 */
class DockerExecutionConfigRegistryAuthTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // extractRegistry() TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @ParameterizedTest
    @CsvSource({
        // Official Docker Hub images
        "python:3.12-slim, docker.io",
        "node:20-alpine, docker.io",
        "alpine:latest, docker.io",
        "ubuntu, docker.io",

        // Docker Hub user/organization images
        "library/python:3.12, docker.io",
        "bitnami/redis:latest, docker.io",
        "myuser/myimage:v1, docker.io",

        // GitHub Container Registry
        "ghcr.io/owner/image:tag, ghcr.io",
        "ghcr.io/myorg/myapp:latest, ghcr.io",

        // Google Container Registry
        "gcr.io/project/image:tag, gcr.io",
        "us.gcr.io/project/image, us.gcr.io",
        "eu.gcr.io/project/image:v1, eu.gcr.io",

        // AWS ECR
        "123456789.dkr.ecr.us-east-1.amazonaws.com/myapp:latest, 123456789.dkr.ecr.us-east-1.amazonaws.com",
        "987654321.dkr.ecr.eu-west-1.amazonaws.com/service:v2, 987654321.dkr.ecr.eu-west-1.amazonaws.com",

        // Azure Container Registry
        "myregistry.azurecr.io/app:latest, myregistry.azurecr.io",
        "company.azurecr.io/service/api:v1, company.azurecr.io",

        // Private registry with port
        "registry.company.com:5000/image:tag, registry.company.com:5000",
        "localhost:5000/myimage:latest, localhost:5000",

        // Localhost registry
        "localhost/myimage:latest, localhost"
    })
    void should_extract_registry_from_image_name(String imageName, String expectedRegistry) {
        String registry = DockerExecutionConfig.extractRegistry(imageName);
        assertThat(registry).isEqualTo(expectedRegistry);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void should_default_to_docker_io_for_blank_image(String imageName) {
        String registry = DockerExecutionConfig.extractRegistry(imageName);
        assertThat(registry).isEqualTo("docker.io");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REGISTRY AUTH BUILDER TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void should_add_registry_auth_using_config_object() {
        RegistryAuthConfig authConfig = RegistryAuthConfig.of("ghcr.io", "user", "token");

        DockerExecutionConfig config =
                DockerExecutionConfig.builder().registryAuth(authConfig).build();

        assertThat(config.registryAuths()).hasSize(1);
        assertThat(config.getRegistryAuth("ghcr.io")).isNotNull();
        assertThat(config.getRegistryAuth("ghcr.io").username()).isEqualTo("user");
    }

    @Test
    void should_add_registry_auth_using_convenience_method() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .registryAuth("docker.io", "dockeruser", "dockertoken")
                .build();

        assertThat(config.registryAuths()).hasSize(1);
        assertThat(config.getRegistryAuth("docker.io")).isNotNull();
        assertThat(config.getRegistryAuth("docker.io").username()).isEqualTo("dockeruser");
    }

    @Test
    void should_support_multiple_registry_auths() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .registryAuth("ghcr.io", "github-user", "github-token")
                .registryAuth("docker.io", "docker-user", "docker-token")
                .registryAuth("gcr.io", "_json_key", "service-account-key")
                .build();

        assertThat(config.registryAuths()).hasSize(3);
        assertThat(config.getRegistryAuth("ghcr.io")).isNotNull();
        assertThat(config.getRegistryAuth("docker.io")).isNotNull();
        assertThat(config.getRegistryAuth("gcr.io")).isNotNull();
    }

    @Test
    void should_replace_duplicate_registry_auth() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .registryAuth("ghcr.io", "old-user", "old-token")
                .registryAuth("ghcr.io", "new-user", "new-token")
                .build();

        assertThat(config.registryAuths()).hasSize(1);
        assertThat(config.getRegistryAuth("ghcr.io").username()).isEqualTo("new-user");
    }

    @Test
    void should_set_all_registry_auths_at_once() {
        Map<String, RegistryAuthConfig> auths = Map.of(
                "ghcr.io", RegistryAuthConfig.of("ghcr.io", "user1", "token1"),
                "docker.io", RegistryAuthConfig.of("docker.io", "user2", "token2"));

        DockerExecutionConfig config =
                DockerExecutionConfig.builder().registryAuths(auths).build();

        assertThat(config.registryAuths()).hasSize(2);
    }

    @Test
    void should_return_null_for_unconfigured_registry() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .registryAuth("ghcr.io", "user", "token")
                .build();

        assertThat(config.getRegistryAuth("docker.io")).isNull();
        assertThat(config.getRegistryAuth("unknown.registry.io")).isNull();
    }

    @Test
    void should_have_empty_registry_auths_by_default() {
        DockerExecutionConfig config = DockerExecutionConfig.builder().build();

        assertThat(config.registryAuths()).isEmpty();
    }

    @Test
    void registry_auths_should_be_immutable() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .registryAuth("ghcr.io", "user", "token")
                .build();

        Map<String, RegistryAuthConfig> auths = config.registryAuths();

        assertThat(auths).isUnmodifiable();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TLS CERT PATH TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void should_set_tls_cert_path() {
        DockerExecutionConfig config =
                DockerExecutionConfig.builder().tlsCertPath("/path/to/certs").build();

        assertThat(config.tlsCertPath()).isEqualTo("/path/to/certs");
    }

    @Test
    void should_have_null_tls_cert_path_by_default() {
        DockerExecutionConfig config = DockerExecutionConfig.builder().build();

        assertThat(config.tlsCertPath()).isNull();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // REMOTE DOCKER CONFIGURATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void should_configure_remote_docker_with_registry_auth() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .dockerHost("tcp://docker.example.com:2376")
                .tlsVerify(true)
                .tlsCertPath("/home/user/.docker/certs")
                .registryAuth("ghcr.io", "github-user", "github-token")
                .registryAuth("docker.io", "dockerhub-user", "dockerhub-token")
                .build();

        assertThat(config.dockerHost()).isEqualTo("tcp://docker.example.com:2376");
        assertThat(config.tlsVerify()).isTrue();
        assertThat(config.tlsCertPath()).isEqualTo("/home/user/.docker/certs");
        assertThat(config.registryAuths()).hasSize(2);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // toString TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void toString_should_show_registry_count_not_credentials() {
        DockerExecutionConfig config = DockerExecutionConfig.builder()
                .registryAuth("ghcr.io", "user", "super-secret")
                .registryAuth("docker.io", "user2", "another-secret")
                .build();

        String str = config.toString();

        assertThat(str).contains("2 configured");
        assertThat(str).doesNotContain("super-secret");
        assertThat(str).doesNotContain("another-secret");
    }
}
