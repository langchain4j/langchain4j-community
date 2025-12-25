package dev.langchain4j.community.code.docker;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for Docker code execution with security constraints.
 * Includes memory limits, timeout, network isolation, and capability dropping.
 * Use {@link #builder()} to create instances.
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
        this.capDrop = Collections.unmodifiableList(new ArrayList<>(builder.capDrop));
        this.user = builder.user;
        this.workingDir = builder.workingDir;
        this.environmentVariables = Collections.unmodifiableMap(new HashMap<>(builder.environmentVariables));
        this.dockerHost = builder.dockerHost;
        this.tlsVerify = builder.tlsVerify;
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

    /** Parses memory string (e.g., "256m", "1g") into bytes. */
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

    /** Creates a new builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for DockerExecutionConfig. */
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

        private Builder() {
        }

        /** Sets memory limit (e.g., "256m", "1g"). */
        public Builder memoryLimit(String memoryLimit) {
            this.memoryLimit = ensureNotBlank(memoryLimit, "memoryLimit");
            return this;
        }

        /** Sets memory swap limit in bytes (-1 for unlimited). */
        public Builder memorySwapBytes(Long memorySwapBytes) {
            this.memorySwapBytes = memorySwapBytes;
            return this;
        }

        /** Sets CPU period in microseconds for CFS scheduler. */
        public Builder cpuPeriodMicros(Long cpuPeriodMicros) {
            this.cpuPeriodMicros = cpuPeriodMicros;
            return this;
        }

        /** Sets CPU quota in microseconds for CFS scheduler. */
        public Builder cpuQuotaMicros(Long cpuQuotaMicros) {
            this.cpuQuotaMicros = cpuQuotaMicros;
            return this;
        }

        /** Sets CPU shares (default: 1024). */
        public Builder cpuShares(Integer cpuShares) {
            this.cpuShares = cpuShares;
            return this;
        }

        /** Sets execution timeout. */
        public Builder timeout(Duration timeout) {
            this.timeout = ensureNotNull(timeout, "timeout");
            return this;
        }

        /** Sets maximum output size in bytes. */
        public Builder maxOutputSizeBytes(int maxOutputSizeBytes) {
            this.maxOutputSizeBytes = ensureGreaterThanZero(maxOutputSizeBytes, "maxOutputSizeBytes");
            return this;
        }

        /** Sets whether network is disabled (default: true). Enabling allows data exfiltration. */
        public Builder networkDisabled(boolean networkDisabled) {
            this.networkDisabled = networkDisabled;
            return this;
        }

        /** Sets whether root filesystem is read-only. */
        public Builder readOnlyRootfs(boolean readOnlyRootfs) {
            this.readOnlyRootfs = readOnlyRootfs;
            return this;
        }

        /** Sets Linux capabilities to drop (default: ["ALL"]). */
        public Builder capDrop(List<String> capDrop) {
            this.capDrop = ensureNotNull(capDrop, "capDrop");
            return this;
        }

        /** Sets container user (e.g., "1000:1000", "nobody"). */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /** Sets working directory inside container. */
        public Builder workingDir(String workingDir) {
            this.workingDir = ensureNotBlank(workingDir, "workingDir");
            return this;
        }

        /** Sets environment variables for container. */
        public Builder environmentVariables(Map<String, String> environmentVariables) {
            this.environmentVariables = ensureNotNull(environmentVariables, "environmentVariables");
            return this;
        }

        /** Adds a single environment variable. */
        public Builder addEnvironmentVariable(String key, String value) {
            this.environmentVariables.put(
                    ensureNotBlank(key, "key"),
                    ensureNotNull(value, "value")
            );
            return this;
        }

        /** Sets Docker host URI. */
        public Builder dockerHost(String dockerHost) {
            this.dockerHost = dockerHost;
            return this;
        }

        /** Sets whether TLS verification is enabled. */
        public Builder tlsVerify(boolean tlsVerify) {
            this.tlsVerify = tlsVerify;
            return this;
        }

        /** Builds the configuration. */
        public DockerExecutionConfig build() {
            return new DockerExecutionConfig(this);
        }
    }

    @Override
    public String toString() {
        return "DockerExecutionConfig{" +
                "memoryLimit='" + memoryLimit + '\'' +
                ", timeout=" + timeout +
                ", networkDisabled=" + networkDisabled +
                ", readOnlyRootfs=" + readOnlyRootfs +
                ", capDrop=" + capDrop +
                ", workingDir='" + workingDir + '\'' +
                '}';
    }
}
