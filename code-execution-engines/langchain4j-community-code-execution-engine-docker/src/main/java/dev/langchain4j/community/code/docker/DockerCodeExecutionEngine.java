package dev.langchain4j.community.code.docker;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A secure code execution engine that runs untrusted code in isolated Docker containers.
 *
 * <p>Provides a sandboxed environment for executing code with security constraints including
 * network isolation, memory/CPU limits, and Linux capability dropping.
 *
 * <p>This class is thread-safe and implements {@link Closeable}.
 *
 * @see DockerExecutionConfig for configuration options
 * @see DockerCodeExecutionTool for LLM tool integration
 */
public class DockerCodeExecutionEngine implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerCodeExecutionEngine.class);

    private static final String DEFAULT_FILENAME_PREFIX = "code";

    private final DockerClient dockerClient;
    private final DockerExecutionConfig config;
    private final DockerContainerManager containerManager;
    private final boolean ownsDockerClient;

    /**
     * Creates a new engine with the specified configuration, creating its own Docker client.
     *
     * @param config the execution configuration
     * @throws NullPointerException if config is null
     * @throws DockerExecutionException if Docker daemon is not available
     */
    public DockerCodeExecutionEngine(DockerExecutionConfig config) {
        this.config = ensureNotNull(config, "config");
        this.dockerClient = createDockerClient(config);
        this.containerManager = new DockerContainerManager(dockerClient, config);
        this.ownsDockerClient = true;
    }

    /**
     * Creates a new engine with a provided Docker client (caller manages client lifecycle).
     *
     * @param dockerClient the pre-configured Docker client to use
     * @param config the execution configuration
     * @throws NullPointerException if dockerClient or config is null
     */
    public DockerCodeExecutionEngine(DockerClient dockerClient, DockerExecutionConfig config) {
        this.config = ensureNotNull(config, "config");
        this.dockerClient = ensureNotNull(dockerClient, "dockerClient");
        this.containerManager = new DockerContainerManager(dockerClient, config);
        this.ownsDockerClient = false;
    }

    /**
     * Creates a new engine with secure default configuration.
     *
     * @return a new engine instance with secure defaults
     * @throws DockerExecutionException if Docker daemon is not available
     */
    public static DockerCodeExecutionEngine withDefaultConfig() {
        return new DockerCodeExecutionEngine(DockerExecutionConfig.builder().build());
    }

    /**
     * Executes code in an isolated Docker container.
     *
     * <p>Creates a container, copies the code, executes it, and cleans up. Images are
     * automatically pulled if not available locally.
     *
     * @param image the Docker image (e.g., "python:3.12-slim")
     * @param fileExtension the file extension (e.g., ".py", ".js")
     * @param code the source code to execute
     * @param command the command to run (e.g., "python", "node")
     * @param timeout the maximum execution time
     * @return the trimmed stdout, or empty string if no output
     * @throws IllegalArgumentException if any parameter is null or blank
     * @throws DockerExecutionException if execution fails
     */
    public String execute(String image, String fileExtension, String code, String command, Duration timeout) {
        ensureNotBlank(image, "image");
        ensureNotBlank(fileExtension, "fileExtension");
        ensureNotBlank(code, "code");
        ensureNotBlank(command, "command");
        ensureNotNull(timeout, "timeout");

        LOGGER.debug(
                "Executing code with image: {}, extension: {}, command: {}, timeout: {}",
                image,
                fileExtension,
                command,
                timeout);

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
            int exitCode = containerManager.waitForCompletion(containerId, timeout);

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

    private String buildFilename(String fileExtension) {
        String ext = fileExtension.startsWith(".") ? fileExtension : "." + fileExtension;
        return DEFAULT_FILENAME_PREFIX + ext;
    }

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
        return new String[] {"sh", "-c", processedCommand};
    }

    private String trimOutput(String output) {
        return output != null ? output.trim() : "";
    }

    /**
     * Checks if the Docker daemon is available and responsive.
     *
     * @return {@code true} if Docker daemon responded to ping, {@code false} otherwise
     */
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

    /**
     * Closes this engine. Only closes the Docker client if this engine owns it.
     */
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

    private static DockerClient createDockerClient(DockerExecutionConfig config) {
        try {
            DefaultDockerClientConfig.Builder configBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder();

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
