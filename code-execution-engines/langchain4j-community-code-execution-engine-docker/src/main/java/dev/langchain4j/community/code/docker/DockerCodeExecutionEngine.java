package dev.langchain4j.community.code.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Executes code in isolated Docker containers with security constraints.
 * LLM provides runtime parameters (image, code, command) while the engine enforces security.
 */
public class DockerCodeExecutionEngine implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerCodeExecutionEngine.class);

    private static final String DEFAULT_FILENAME_PREFIX = "code";

    private final DockerClient dockerClient;
    private final DockerExecutionConfig config;
    private final DockerContainerManager containerManager;
    private final boolean ownsDockerClient;

    /**
     * Creates a new engine with the specified configuration.
     * The engine creates and owns its own DockerClient.
     */
    public DockerCodeExecutionEngine(DockerExecutionConfig config) {
        this.config = ensureNotNull(config, "config");
        this.dockerClient = createDockerClient(config);
        this.containerManager = new DockerContainerManager(dockerClient, config);
        this.ownsDockerClient = true;
    }

    /**
     * Creates a new engine with a provided DockerClient.
     * The caller is responsible for closing the DockerClient.
     */
    public DockerCodeExecutionEngine(DockerClient dockerClient, DockerExecutionConfig config) {
        this.config = ensureNotNull(config, "config");
        this.dockerClient = ensureNotNull(dockerClient, "dockerClient");
        this.containerManager = new DockerContainerManager(dockerClient, config);
        this.ownsDockerClient = false;
    }

    /** Creates a new engine with secure default configuration. */
    public static DockerCodeExecutionEngine withDefaultConfig() {
        return new DockerCodeExecutionEngine(DockerExecutionConfig.builder().build());
    }

    /**
     * Executes code in a Docker container and returns stdout.
     * Creates container, copies code, runs command, and cleans up.
     */
    public String execute(String image, String fileExtension, String code, String command) {
        ensureNotBlank(image, "image");
        ensureNotBlank(fileExtension, "fileExtension");
        ensureNotBlank(code, "code");
        ensureNotBlank(command, "command");

        LOGGER.debug("Executing code with image: {}, extension: {}, command: {}",
                image, fileExtension, command);

        // Build filename with proper extension
        String filename = buildFilename(fileExtension);

        // Build command array - handles commands like "go run code.go"
        String[] commandArray = buildCommandArray(command, filename);

        String containerId = null;
        try {
            // 1. Create container
            containerId = containerManager.createContainer(image, commandArray);

            // 2. Copy code to container
            containerManager.copyCodeToContainer(containerId, code, filename);

            // 3. Start container and wait for completion
            containerManager.startContainer(containerId);
            int exitCode = containerManager.waitForCompletion(containerId, config.timeout());

            // 4. Get output
            String stdout = containerManager.getStdout(containerId);
            String stderr = containerManager.getStderr(containerId);

            // 5. Check exit code
            if (exitCode != 0) {
                throw DockerExecutionException.executionFailed(image, exitCode, stderr);
            }

            LOGGER.debug("Execution completed successfully");
            return trimOutput(stdout);

        } finally {
            // 6. Always cleanup container
            if (containerId != null) {
                containerManager.forceRemoveContainer(containerId);
            }
        }
    }

    /** Builds filename from file extension, ensuring it starts with a dot. */
    private String buildFilename(String fileExtension) {
        String ext = fileExtension.startsWith(".") ? fileExtension : "." + fileExtension;
        return DEFAULT_FILENAME_PREFIX + ext;
    }

    /** Builds command array, appending filename if not already present. */
    private String[] buildCommandArray(String command, String filename) {
        String processedCommand;

        // If command already contains the filename, use as-is
        if (command.contains(filename)) {
            processedCommand = command;
        } else {
            // Simple case: append filename to command
            // e.g., "python" -> "python code.py"
            processedCommand = command + " " + filename;
        }

        // Use shell to handle complex commands
        return new String[]{"sh", "-c", processedCommand};
    }

    /** Trims whitespace from output. */
    private String trimOutput(String output) {
        return output != null ? output.trim() : "";
    }

    /** Checks if Docker daemon is available and responsive. */
    public boolean isAvailable() {
        try {
            dockerClient.pingCmd().exec();
            return true;
        } catch (Exception e) {
            LOGGER.debug("Docker is not available: {}", e.getMessage());
            return false;
        }
    }

    /** Returns the configuration used by this engine. */
    public DockerExecutionConfig getConfig() {
        return config;
    }

    /** Closes the engine. If this engine owns the DockerClient, it will be closed. */
    @Override
    public void close() {
        if (ownsDockerClient && dockerClient != null) {
            try {
                dockerClient.close();
                LOGGER.debug("Docker client closed");
            } catch (IOException e) {
                LOGGER.warn("Error closing Docker client: {}", e.getMessage());
            }
        }
    }

    /** Creates a Docker client with the specified configuration. */
    private static DockerClient createDockerClient(DockerExecutionConfig config) {
        try {
            DefaultDockerClientConfig.Builder configBuilder = DefaultDockerClientConfig
                    .createDefaultConfigBuilder();

            if (config.dockerHost() != null) {
                configBuilder.withDockerHost(config.dockerHost());
            }

            if (config.tlsVerify()) {
                configBuilder.withDockerTlsVerify(true);
            }

            if (config.tlsCertPath() != null) {
                configBuilder.withDockerCertPath(config.tlsCertPath());
            }

            DockerClientConfig clientConfig = configBuilder.build();

            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(clientConfig.getDockerHost())
                    .sslConfig(clientConfig.getSSLConfig())
                    .maxConnections(100)
                    .connectionTimeout(Duration.ofSeconds(30))
                    .responseTimeout(Duration.ofSeconds(45))
                    .build();

            return DockerClientImpl.getInstance(clientConfig, httpClient);
        } catch (Exception e) {
            throw DockerExecutionException.dockerNotAvailable(e);
        }
    }
}
