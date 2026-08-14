package dev.langchain4j.community.code.docker;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Authentication configuration for a Docker registry.
 *
 * <p>Supports Docker Hub, GitHub Container Registry (ghcr.io), AWS ECR,
 * Google GCR, Azure ACR, and private registries.
 *
 * @see DockerExecutionConfig.Builder#registryAuth(RegistryAuthConfig)
 */
public final class RegistryAuthConfig {

    private final String registry;
    private final String username;
    private final String password;
    private final String email;

    private RegistryAuthConfig(Builder builder) {
        this.registry = builder.registry;
        this.username = builder.username;
        this.password = builder.password;
        this.email = builder.email;
    }

    /**
     * Returns the registry address.
     *
     * @return the registry address (e.g., "ghcr.io", "docker.io"), never null
     */
    public String registry() {
        return registry;
    }

    /**
     * Returns the username for authentication.
     *
     * @return the username, never null
     */
    public String username() {
        return username;
    }

    /**
     * Returns the password or access token for authentication.
     *
     * @return the password or token, never null
     */
    public String password() {
        return password;
    }

    /**
     * Returns the email address for authentication.
     *
     * @return the email address, or null if not specified
     */
    public String email() {
        return email;
    }

    /**
     * Creates a new builder for constructing RegistryAuthConfig instances.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a RegistryAuthConfig with the specified credentials.
     *
     * @param registry the registry address (e.g., "ghcr.io", "docker.io")
     * @param username the username or account identifier
     * @param password the password or access token
     * @return a new RegistryAuthConfig instance
     * @throws IllegalArgumentException if any parameter is blank
     */
    public static RegistryAuthConfig of(String registry, String username, String password) {
        return builder()
                .registry(registry)
                .username(username)
                .password(password)
                .build();
    }

    /**
     * Builder for creating {@link RegistryAuthConfig} instances.
     *
     * <p>Registry, username, and password are required. Email is optional.
     */
    public static class Builder {

        private String registry;
        private String username;
        private String password;
        private String email;

        private Builder() {}

        /**
         * Sets the Docker registry address.
         *
         * @param registry the registry address (e.g., "ghcr.io", "docker.io")
         * @return this builder
         * @throws IllegalArgumentException if registry is blank
         */
        public Builder registry(String registry) {
            this.registry = ensureNotBlank(registry, "registry");
            return this;
        }

        /**
         * Sets the username for authentication.
         *
         * @param username the username or account identifier
         * @return this builder
         * @throws IllegalArgumentException if username is blank
         */
        public Builder username(String username) {
            this.username = ensureNotBlank(username, "username");
            return this;
        }

        /**
         * Sets the password or access token for authentication.
         *
         * @param password the password or access token
         * @return this builder
         * @throws IllegalArgumentException if password is blank
         */
        public Builder password(String password) {
            this.password = ensureNotBlank(password, "password");
            return this;
        }

        /**
         * Sets the email address for authentication (optional).
         *
         * @param email the email address, or null to omit
         * @return this builder
         */
        public Builder email(String email) {
            this.email = email;
            return this;
        }

        /**
         * Builds an immutable {@link RegistryAuthConfig} with the configured values.
         *
         * @return the built configuration instance
         * @throws IllegalArgumentException if registry, username, or password is blank
         */
        public RegistryAuthConfig build() {
            ensureNotBlank(registry, "registry");
            ensureNotBlank(username, "username");
            ensureNotBlank(password, "password");
            return new RegistryAuthConfig(this);
        }
    }

    @Override
    public String toString() {
        return "RegistryAuthConfig{" + "registry='"
                + registry + '\'' + ", username='"
                + username + '\'' + ", password='***'"
                + // Never log passwords
                '}';
    }
}
