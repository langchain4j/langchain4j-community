package dev.langchain4j.community.code.docker;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

/**
 * Authentication configuration for a Docker registry.
 * Used for private registries on remote Docker daemons.
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

    /** Returns the registry address (e.g., "ghcr.io", "docker.io"). */
    public String registry() {
        return registry;
    }

    /** Returns the username for authentication. */
    public String username() {
        return username;
    }

    /** Returns the password or token for authentication. */
    public String password() {
        return password;
    }

    /** Returns the email for authentication (optional). */
    public String email() {
        return email;
    }

    /** Creates a new builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Creates a RegistryAuthConfig with registry, username, and password. */
    public static RegistryAuthConfig of(String registry, String username, String password) {
        return builder()
                .registry(registry)
                .username(username)
                .password(password)
                .build();
    }

    /** Builder for RegistryAuthConfig. */
    public static class Builder {

        private String registry;
        private String username;
        private String password;
        private String email;

        private Builder() {}

        /** Sets the registry address (e.g., "ghcr.io", "docker.io"). */
        public Builder registry(String registry) {
            this.registry = ensureNotBlank(registry, "registry");
            return this;
        }

        /** Sets the username for authentication. */
        public Builder username(String username) {
            this.username = ensureNotBlank(username, "username");
            return this;
        }

        /** Sets the password or token for authentication. */
        public Builder password(String password) {
            this.password = ensureNotBlank(password, "password");
            return this;
        }

        /** Sets the email for authentication (optional). */
        public Builder email(String email) {
            this.email = email;
            return this;
        }

        /** Builds the RegistryAuthConfig. */
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
