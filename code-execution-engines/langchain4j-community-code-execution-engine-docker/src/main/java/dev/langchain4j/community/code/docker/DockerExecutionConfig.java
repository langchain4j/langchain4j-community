package dev.langchain4j.community.code.docker;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable configuration for Docker code execution with security constraints.
 *
 * <p>Encapsulates resource limits, security policies, and Docker connection settings.
 * Default configuration prioritizes security: network disabled, 256MB memory, 30s timeout,
 * all capabilities dropped.
 *
 * <p>This class is immutable and thread-safe.
 *
 * @see DockerCodeExecutionEngine
 */
public final class DockerExecutionConfig {

    // Resource limits
    private final String memoryLimit;
    private final Long memorySwapBytes;
    private final Long cpuPeriodMicros;
    private final Long cpuQuotaMicros;
    private final Integer cpuShares;

    // Execution limits
    private final Duration timeout;
    private final int maxOutputSizeBytes;

    // Security settings
    private final boolean networkDisabled;
    private final boolean readOnlyRootfs;
    private final List<String> capDrop;
    private final String user;

    // Container settings
    private final String workingDir;
    private final Map<String, String> environmentVariables;

    // Docker connection
    private final String dockerHost;
    private final boolean tlsVerify;
    private final String tlsCertPath;

    // Registry authentication
    private final Map<String, RegistryAuthConfig> registryAuths;

    private DockerExecutionConfig(Builder builder) {
        this.memoryLimit = builder.memoryLimit;
        this.memorySwapBytes = builder.memorySwapBytes;
        this.cpuPeriodMicros = builder.cpuPeriodMicros;
        this.cpuQuotaMicros = builder.cpuQuotaMicros;
        this.cpuShares = builder.cpuShares;
        this.timeout = builder.timeout;
        this.maxOutputSizeBytes = builder.maxOutputSizeBytes;
        this.networkDisabled = builder.networkDisabled;
        this.readOnlyRootfs = builder.readOnlyRootfs;
        this.capDrop = List.copyOf(builder.capDrop);
        this.user = builder.user;
        this.workingDir = builder.workingDir;
        this.environmentVariables = Map.copyOf(builder.environmentVariables);
        this.dockerHost = builder.dockerHost;
        this.tlsVerify = builder.tlsVerify;
        this.tlsCertPath = builder.tlsCertPath;
        this.registryAuths = Map.copyOf(builder.registryAuths);
    }

    /** Returns the memory limit (e.g., "256m", "1g"). */
    public String memoryLimit() {
        return memoryLimit;
    }

    /** Returns memory limit in bytes, or null if not set. */
    public Long memoryLimitBytes() {
        if (memoryLimit == null || memoryLimit.isEmpty()) {
            return null;
        }
        return parseMemoryString(memoryLimit);
    }

    /** Returns memory swap limit in bytes, or null if not set. */
    public Long memorySwapBytes() {
        return memorySwapBytes;
    }

    /** Returns CPU period in microseconds for CFS scheduler. */
    public Long cpuPeriodMicros() {
        return cpuPeriodMicros;
    }

    /** Returns CPU quota in microseconds for CFS scheduler. */
    public Long cpuQuotaMicros() {
        return cpuQuotaMicros;
    }

    /** Returns CPU shares (relative weight). */
    public Integer cpuShares() {
        return cpuShares;
    }

    /** Returns execution timeout. */
    public Duration timeout() {
        return timeout;
    }

    /** Returns maximum output size in bytes. */
    public int maxOutputSizeBytes() {
        return maxOutputSizeBytes;
    }

    /** Returns true if network is disabled. */
    public boolean networkDisabled() {
        return networkDisabled;
    }

    /** Returns true if root filesystem is read-only. */
    public boolean readOnlyRootfs() {
        return readOnlyRootfs;
    }

    /** Returns Linux capabilities to drop. */
    public List<String> capDrop() {
        return capDrop;
    }

    /** Returns container user (e.g., "1000:1000"). */
    public String user() {
        return user;
    }

    /** Returns working directory inside container. */
    public String workingDir() {
        return workingDir;
    }

    /** Returns environment variables for container. */
    public Map<String, String> environmentVariables() {
        return environmentVariables;
    }

    /** Returns Docker host URI. */
    public String dockerHost() {
        return dockerHost;
    }

    /** Returns true if TLS verification is enabled. */
    public boolean tlsVerify() {
        return tlsVerify;
    }

    /** Returns the path to TLS certificates for secure Docker connections. */
    public String tlsCertPath() {
        return tlsCertPath;
    }

    /** Returns the registry authentication configurations. */
    public Map<String, RegistryAuthConfig> registryAuths() {
        return registryAuths;
    }

    /** Gets the authentication config for a specific registry. */
    public RegistryAuthConfig getRegistryAuth(String registry) {
        return registryAuths.get(registry);
    }

    /**
     * Extracts the registry address from a Docker image name.
     *
     * @param imageName the Docker image name to parse
     * @return the registry address, defaulting to "docker.io" for Docker Hub images
     */
    public static String extractRegistry(String imageName) {
        if (imageName == null || imageName.isEmpty()) {
            return "docker.io";
        }

        // Check if image contains a registry (has a dot or colon before the first slash)
        int slashIndex = imageName.indexOf('/');
        if (slashIndex == -1) {
            // No slash means Docker Hub official image (e.g., "python:3.12")
            return "docker.io";
        }

        String potentialRegistry = imageName.substring(0, slashIndex);

        // Check if it looks like a registry (has a dot, colon, or is "localhost")
        if (potentialRegistry.contains(".")
                || potentialRegistry.contains(":")
                || potentialRegistry.equals("localhost")) {
            return potentialRegistry;
        }

        // Otherwise it's a Docker Hub user image (e.g., "library/python")
        return "docker.io";
    }

    private static long parseMemoryString(String memoryString) {
        String value = memoryString.trim().toLowerCase();
        long multiplier = 1;

        if (value.endsWith("k") || value.endsWith("kb")) {
            multiplier = 1024L;
            value = value.replaceAll("[kK][bB]?$", "");
        } else if (value.endsWith("m") || value.endsWith("mb")) {
            multiplier = 1024L * 1024L;
            value = value.replaceAll("[mM][bB]?$", "");
        } else if (value.endsWith("g") || value.endsWith("gb")) {
            multiplier = 1024L * 1024L * 1024L;
            value = value.replaceAll("[gG][bB]?$", "");
        }

        return Long.parseLong(value) * multiplier;
    }

    /** Creates a new builder with secure default values. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for creating {@link DockerExecutionConfig} instances with secure defaults. */
    public static class Builder {

        // Secure defaults
        private String memoryLimit = "256m";
        private Long memorySwapBytes = null;
        private Long cpuPeriodMicros = null;
        private Long cpuQuotaMicros = null;
        private Integer cpuShares = 1024;
        private Duration timeout = Duration.ofSeconds(30);
        private int maxOutputSizeBytes = 1024 * 1024; // 1MB
        private boolean networkDisabled = true;
        private boolean readOnlyRootfs = false;
        private List<String> capDrop = List.of("ALL");
        private String user = null;
        private String workingDir = "/app";
        private Map<String, String> environmentVariables = new HashMap<>();
        private String dockerHost = null;
        private boolean tlsVerify = false;
        private String tlsCertPath = null;
        private Map<String, RegistryAuthConfig> registryAuths = new HashMap<>();

        private Builder() {}

        /** Sets the container memory limit (e.g., "256m", "1g"). */
        public Builder memoryLimit(String memoryLimit) {
            this.memoryLimit = ensureNotBlank(memoryLimit, "memoryLimit");
            return this;
        }

        /** Sets the memory swap limit in bytes (-1 for unlimited). */
        public Builder memorySwapBytes(Long memorySwapBytes) {
            this.memorySwapBytes = memorySwapBytes;
            return this;
        }

        /** Sets the CPU period in microseconds for the CFS scheduler. */
        public Builder cpuPeriodMicros(Long cpuPeriodMicros) {
            this.cpuPeriodMicros = cpuPeriodMicros;
            return this;
        }

        /** Sets the CPU quota in microseconds for the CFS scheduler (-1 for no limit). */
        public Builder cpuQuotaMicros(Long cpuQuotaMicros) {
            this.cpuQuotaMicros = cpuQuotaMicros;
            return this;
        }

        /** Sets the CPU shares (relative weight, default: 1024). */
        public Builder cpuShares(Integer cpuShares) {
            this.cpuShares = cpuShares;
            return this;
        }

        /** Sets the maximum execution time (default: 30 seconds). */
        public Builder timeout(Duration timeout) {
            this.timeout = ensureNotNull(timeout, "timeout");
            return this;
        }

        /** Sets the maximum stdout/stderr size in bytes (default: 1MB). */
        public Builder maxOutputSizeBytes(int maxOutputSizeBytes) {
            this.maxOutputSizeBytes = ensureGreaterThanZero(maxOutputSizeBytes, "maxOutputSizeBytes");
            return this;
        }

        /** Sets whether network is disabled (default: true). */
        public Builder networkDisabled(boolean networkDisabled) {
            this.networkDisabled = networkDisabled;
            return this;
        }

        /**
         * Sets whether the container's root filesystem is read-only.
         *
         * <p>Enabling this prevents code from modifying system files, adding security.
         * However, some images may not work with a read-only filesystem.
         *
         * @param readOnlyRootfs true to make root filesystem read-only
         * @return this builder
         */
        public Builder readOnlyRootfs(boolean readOnlyRootfs) {
            this.readOnlyRootfs = readOnlyRootfs;
            return this;
        }

        /**
         * Sets the Linux capabilities to drop from the container.
         *
         * <p>By default, ALL capabilities are dropped for maximum security.
         * Capabilities control what privileged operations a container can perform.
         *
         * <p>Examples:
         * <ul>
         *   <li>{@code ["ALL"]} - Drop all capabilities (most secure)</li>
         *   <li>{@code ["NET_RAW", "SYS_ADMIN"]} - Drop specific capabilities</li>
         * </ul>
         *
         * @param capDrop the list of capabilities to drop
         * @return this builder
         * @throws NullPointerException if capDrop is null
         */
        public Builder capDrop(List<String> capDrop) {
            this.capDrop = ensureNotNull(capDrop, "capDrop");
            return this;
        }

        /**
         * Sets the user to run the container as.
         *
         * <p>Running as a non-root user adds security by preventing privilege escalation.
         * Formats: "username", "uid", "uid:gid", "username:groupname".
         *
         * @param user the user specification (e.g., "1000:1000", "nobody")
         * @return this builder
         */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * Sets the working directory inside the container.
         *
         * <p>This is where the code file is copied to and the command is executed from.
         *
         * @param workingDir the container working directory path (default: "/app")
         * @return this builder
         * @throws IllegalArgumentException if workingDir is blank
         */
        public Builder workingDir(String workingDir) {
            this.workingDir = ensureNotBlank(workingDir, "workingDir");
            return this;
        }

        /**
         * Sets environment variables for the container.
         *
         * <p>These variables are available to the executed code.
         *
         * @param environmentVariables a map of variable names to values
         * @return this builder
         * @throws NullPointerException if environmentVariables is null
         */
        public Builder environmentVariables(Map<String, String> environmentVariables) {
            this.environmentVariables = ensureNotNull(environmentVariables, "environmentVariables");
            return this;
        }

        /**
         * Adds a single environment variable.
         *
         * @param key the variable name
         * @param value the variable value
         * @return this builder
         * @throws IllegalArgumentException if key is blank
         * @throws NullPointerException if value is null
         */
        public Builder addEnvironmentVariable(String key, String value) {
            this.environmentVariables.put(ensureNotBlank(key, "key"), ensureNotNull(value, "value"));
            return this;
        }

        /**
         * Sets the Docker daemon host URI.
         *
         * <p>Examples:
         * <ul>
         *   <li>{@code unix:///var/run/docker.sock} - Local Unix socket</li>
         *   <li>{@code tcp://localhost:2375} - Local TCP (unencrypted)</li>
         *   <li>{@code tcp://docker.example.com:2376} - Remote TCP (use with TLS)</li>
         * </ul>
         *
         * @param dockerHost the Docker daemon URI (null for default)
         * @return this builder
         */
        public Builder dockerHost(String dockerHost) {
            this.dockerHost = dockerHost;
            return this;
        }

        /**
         * Sets whether TLS verification is enabled for Docker daemon connections.
         *
         * <p>Should be enabled when connecting to remote Docker daemons over TCP.
         * Use with {@link #tlsCertPath(String)} to specify certificate location.
         *
         * @param tlsVerify true to enable TLS verification
         * @return this builder
         */
        public Builder tlsVerify(boolean tlsVerify) {
            this.tlsVerify = tlsVerify;
            return this;
        }

        /**
         * Sets the path to TLS certificates for secure Docker daemon connections.
         *
         * <p>The directory should contain: ca.pem, cert.pem, and key.pem files.
         *
         * @param tlsCertPath the path to the certificate directory
         * @return this builder
         */
        public Builder tlsCertPath(String tlsCertPath) {
            this.tlsCertPath = tlsCertPath;
            return this;
        }

        /**
         * Adds authentication configuration for a Docker registry.
         *
         * <p>Used when pulling images from private registries.
         *
         * @param authConfig the registry authentication configuration
         * @return this builder
         * @throws NullPointerException if authConfig is null
         * @see RegistryAuthConfig
         */
        public Builder registryAuth(RegistryAuthConfig authConfig) {
            ensureNotNull(authConfig, "authConfig");
            this.registryAuths.put(authConfig.registry(), authConfig);
            return this;
        }

        /**
         * Adds authentication for a Docker registry using username and password.
         *
         * <p>Convenience method for simple authentication. For tokens (like GitHub PAT),
         * use the token as the password.
         *
         * @param registry the registry address (e.g., "ghcr.io", "docker.io")
         * @param username the username or account name
         * @param password the password or access token
         * @return this builder
         */
        public Builder registryAuth(String registry, String username, String password) {
            return registryAuth(RegistryAuthConfig.of(registry, username, password));
        }

        /**
         * Sets all registry authentication configurations at once.
         *
         * <p>Replaces any previously configured registry authentications.
         *
         * @param registryAuths a map of registry addresses to their auth configurations
         * @return this builder
         * @throws NullPointerException if registryAuths is null
         */
        public Builder registryAuths(Map<String, RegistryAuthConfig> registryAuths) {
            this.registryAuths = new HashMap<>(ensureNotNull(registryAuths, "registryAuths"));
            return this;
        }

        /**
         * Builds an immutable {@link DockerExecutionConfig} with the configured settings.
         *
         * @return the built configuration instance
         */
        public DockerExecutionConfig build() {
            return new DockerExecutionConfig(this);
        }
    }

    @Override
    public String toString() {
        return "DockerExecutionConfig{" + "memoryLimit='"
                + memoryLimit + '\'' + ", timeout="
                + timeout + ", networkDisabled="
                + networkDisabled + ", readOnlyRootfs="
                + readOnlyRootfs + ", capDrop="
                + capDrop + ", workingDir='"
                + workingDir + '\'' + ", dockerHost='"
                + dockerHost + '\'' + ", registryAuths="
                + registryAuths.size() + " configured" + '}';
    }
}
