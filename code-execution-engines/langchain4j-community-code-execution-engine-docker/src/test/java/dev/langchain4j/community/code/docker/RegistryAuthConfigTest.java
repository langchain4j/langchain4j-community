package dev.langchain4j.community.code.docker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RegistryAuthConfig}.
 */
class RegistryAuthConfigTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // BUILDER TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void should_build_config_with_all_fields() {
        RegistryAuthConfig config = RegistryAuthConfig.builder()
                .registry("ghcr.io")
                .username("myuser")
                .password("mytoken")
                .email("user@example.com")
                .build();

        assertThat(config.registry()).isEqualTo("ghcr.io");
        assertThat(config.username()).isEqualTo("myuser");
        assertThat(config.password()).isEqualTo("mytoken");
        assertThat(config.email()).isEqualTo("user@example.com");
    }

    @Test
    void should_build_config_without_email() {
        RegistryAuthConfig config = RegistryAuthConfig.builder()
                .registry("docker.io")
                .username("dockeruser")
                .password("dockerpass")
                .build();

        assertThat(config.registry()).isEqualTo("docker.io");
        assertThat(config.username()).isEqualTo("dockeruser");
        assertThat(config.password()).isEqualTo("dockerpass");
        assertThat(config.email()).isNull();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FACTORY METHOD TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void should_create_config_using_static_factory() {
        RegistryAuthConfig config = RegistryAuthConfig.of("ghcr.io", "user", "token");

        assertThat(config.registry()).isEqualTo("ghcr.io");
        assertThat(config.username()).isEqualTo("user");
        assertThat(config.password()).isEqualTo("token");
        assertThat(config.email()).isNull();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // VALIDATION TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @ParameterizedTest
    @NullAndEmptySource
    void should_throw_when_registry_is_blank(String registry) {
        assertThatThrownBy(() -> RegistryAuthConfig.builder()
                .registry(registry)
                .username("user")
                .password("pass")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("registry");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void should_throw_when_username_is_blank(String username) {
        assertThatThrownBy(() -> RegistryAuthConfig.builder()
                .registry("docker.io")
                .username(username)
                .password("pass")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("username");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void should_throw_when_password_is_blank(String password) {
        assertThatThrownBy(() -> RegistryAuthConfig.builder()
                .registry("docker.io")
                .username("user")
                .password(password)
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // COMMON REGISTRY TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void should_support_github_container_registry() {
        RegistryAuthConfig config = RegistryAuthConfig.of("ghcr.io", "github-user", "ghp_token123");

        assertThat(config.registry()).isEqualTo("ghcr.io");
    }

    @Test
    void should_support_docker_hub() {
        RegistryAuthConfig config = RegistryAuthConfig.of("docker.io", "dockerhub-user", "dckr_pat_xxx");

        assertThat(config.registry()).isEqualTo("docker.io");
    }

    @Test
    void should_support_aws_ecr() {
        RegistryAuthConfig config = RegistryAuthConfig.of(
                "123456789.dkr.ecr.us-east-1.amazonaws.com",
                "AWS",
                "ecr-auth-token"
        );

        assertThat(config.registry()).isEqualTo("123456789.dkr.ecr.us-east-1.amazonaws.com");
        assertThat(config.username()).isEqualTo("AWS");
    }

    @Test
    void should_support_google_container_registry() {
        RegistryAuthConfig config = RegistryAuthConfig.of(
                "gcr.io",
                "_json_key",
                "{\"type\":\"service_account\"}"
        );

        assertThat(config.registry()).isEqualTo("gcr.io");
    }

    @Test
    void should_support_azure_container_registry() {
        RegistryAuthConfig config = RegistryAuthConfig.of(
                "myregistry.azurecr.io",
                "client-id",
                "client-secret"
        );

        assertThat(config.registry()).isEqualTo("myregistry.azurecr.io");
    }

    @Test
    void should_support_private_registry_with_port() {
        RegistryAuthConfig config = RegistryAuthConfig.of(
                "registry.internal.company.com:5000",
                "admin",
                "secret"
        );

        assertThat(config.registry()).isEqualTo("registry.internal.company.com:5000");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // toString TESTS
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    void toString_should_mask_password() {
        RegistryAuthConfig config = RegistryAuthConfig.of("ghcr.io", "user", "super-secret-token");

        String str = config.toString();

        assertThat(str).contains("ghcr.io");
        assertThat(str).contains("user");
        assertThat(str).contains("***");
        assertThat(str).doesNotContain("super-secret-token");
    }
}
