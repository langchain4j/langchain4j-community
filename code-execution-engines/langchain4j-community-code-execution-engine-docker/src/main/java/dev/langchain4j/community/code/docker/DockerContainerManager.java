package dev.langchain4j.community.code.docker;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the lifecycle of Docker containers for code execution.
 *
 * <p>Handles container creation, code injection, execution, output collection, and cleanup.
 * Applies security constraints from {@link DockerExecutionConfig}. Images are automatically
 * pulled if not available locally.
 *
 * <p>This class is thread-safe.
 *
 * @see DockerCodeExecutionEngine
 */
public class DockerContainerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerContainerManager.class);

    private final DockerClient dockerClient;
    private final DockerExecutionConfig config;

    /**
     * Creates a new container manager with the specified Docker client and configuration.
     *
     * @param dockerClient the Docker client for API communication
     * @param config the execution configuration with security constraints
     * @throws NullPointerException if either parameter is null
     */
    public DockerContainerManager(DockerClient dockerClient, DockerExecutionConfig config) {
        this.dockerClient = ensureNotNull(dockerClient, "dockerClient");
        this.config = ensureNotNull(config, "config");
    }

    /**
     * Creates a Docker container with the specified image and command.
     *
     * <p>Pulls the image if not available locally, applies security constraints, and creates
     * the container. The container is created but not started.
     *
     * @param image the Docker image to use (e.g., "python:3.12-slim")
     * @param command the command array to execute in the container
     * @return the container ID
     * @throws IllegalArgumentException if image is blank or command is null
     * @throws DockerExecutionException if image pull or container creation fails
     */
    public String createContainer(String image, String[] command) {
        ensureNotBlank(image, "image");
        ensureNotNull(command, "command");

        LOGGER.debug("Creating container with image: {}, command: {}", image, Arrays.toString(command));

        try {
            // Ensure image is available
            pullImageIfNeeded(image);

            // Build host configuration with security constraints
            HostConfig hostConfig = buildHostConfig();

            // Build environment variables list
            List<String> envVars = buildEnvironmentVariables();

            // Create container
            CreateContainerResponse container = dockerClient
                    .createContainerCmd(image)
                    .withHostConfig(hostConfig)
                    .withCmd(command)
                    .withWorkingDir(config.workingDir())
                    .withNetworkDisabled(config.networkDisabled())
                    .withEnv(envVars)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            String containerId = container.getId();
            LOGGER.debug("Created container: {}", containerId);

            return containerId;
        } catch (NotFoundException e) {
            throw DockerExecutionException.imageNotFound(image);
        } catch (DockerException e) {
            throw DockerExecutionException.containerCreateFailed(image, e);
        }
    }

    /**
     * Builds the Docker host configuration with security constraints.
     *
     * <p>This method applies all security settings from the configuration:
     * <ul>
     *   <li>Memory limit and swap limit</li>
     *   <li>CPU period, quota, and shares for CFS scheduler</li>
     *   <li>Network mode ("none" if network disabled)</li>
     *   <li>Read-only root filesystem flag</li>
     *   <li>Linux capabilities to drop</li>
     * </ul>
     *
     * @return a configured HostConfig ready for container creation
     */
    private HostConfig buildHostConfig() {
        HostConfig hostConfig = HostConfig.newHostConfig();

        // Memory limits
        Long memoryLimitBytes = config.memoryLimitBytes();
        if (memoryLimitBytes != null) {
            hostConfig.withMemory(memoryLimitBytes);
        }

        if (config.memorySwapBytes() != null) {
            hostConfig.withMemorySwap(config.memorySwapBytes());
        }

        // CPU limits
        if (config.cpuPeriodMicros() != null) {
            hostConfig.withCpuPeriod(config.cpuPeriodMicros());
        }

        if (config.cpuQuotaMicros() != null) {
            hostConfig.withCpuQuota(config.cpuQuotaMicros());
        }

        if (config.cpuShares() != null) {
            hostConfig.withCpuShares(config.cpuShares());
        }

        // Network mode
        if (config.networkDisabled()) {
            hostConfig.withNetworkMode("none");
        }

        // Read-only root filesystem
        hostConfig.withReadonlyRootfs(config.readOnlyRootfs());

        // Drop capabilities
        List<Capability> capsToDrop = parseCapabilities(config.capDrop());
        if (!capsToDrop.isEmpty()) {
            hostConfig.withCapDrop(capsToDrop.toArray(new Capability[0]));
        }

        return hostConfig;
    }

    /**
     * Builds the environment variables list in Docker's KEY=VALUE format.
     *
     * @return a list of environment variable strings for the container
     */
    private List<String> buildEnvironmentVariables() {
        List<String> envVars = new ArrayList<>();
        for (var entry : config.environmentVariables().entrySet()) {
            envVars.add(entry.getKey() + "=" + entry.getValue());
        }
        return envVars;
    }

    /**
     * Parses capability strings into Docker Capability enum values.
     *
     * <p>The special value "ALL" adds all available capabilities.
     * Unknown capability names are logged as warnings and skipped.
     *
     * @param capStrings the list of capability names (e.g., ["ALL"], ["NET_ADMIN", "SYS_ADMIN"])
     * @return a list of Capability enum values
     */
    private List<Capability> parseCapabilities(List<String> capStrings) {
        List<Capability> capabilities = new ArrayList<>();
        for (String cap : capStrings) {
            if ("ALL".equalsIgnoreCase(cap)) {
                // Add all capabilities
                capabilities.addAll(Arrays.asList(Capability.values()));
            } else {
                try {
                    capabilities.add(Capability.valueOf(cap.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Unknown capability: {}", cap);
                }
            }
        }
        return capabilities;
    }

    /**
     * Pulls the Docker image if not available locally.
     *
     * <p>This method first checks if the image exists locally. If not, it pulls
     * the image from the registry, applying authentication if configured for
     * that registry.
     *
     * <p>The pull operation has a 5-minute timeout.
     *
     * @param image the image to pull (e.g., "python:3.12-slim", "ghcr.io/owner/image:tag")
     * @throws DockerExecutionException if the pull fails or is interrupted
     */
    private void pullImageIfNeeded(String image) {
        try {
            // Check if image exists locally
            dockerClient.inspectImageCmd(image).exec();
            LOGGER.debug("Image {} already available locally", image);
        } catch (NotFoundException e) {
            // Image not found locally, pull it
            LOGGER.info("Pulling image: {}", image);
            try {
                var pullCmd = dockerClient.pullImageCmd(image);

                // Apply authentication if configured for this registry
                AuthConfig authConfig = buildAuthConfig(image);
                if (authConfig != null) {
                    LOGGER.debug("Using registry authentication for: {}", authConfig.getRegistryAddress());
                    pullCmd.withAuthConfig(authConfig);
                }

                pullCmd.start().awaitCompletion(5, TimeUnit.MINUTES);
                LOGGER.debug("Successfully pulled image: {}", image);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw DockerExecutionException.imagePullFailed(image, ie);
            } catch (DockerException de) {
                throw DockerExecutionException.imagePullFailed(image, de);
            }
        }
    }

    /**
     * Builds authentication configuration for pulling an image from a private registry.
     *
     * <p>This method extracts the registry from the image name and looks up
     * authentication credentials from the configuration.
     *
     * @param image the image name to extract registry from
     * @return the AuthConfig for the registry, or null if no auth configured
     */
    private AuthConfig buildAuthConfig(String image) {
        String registry = DockerExecutionConfig.extractRegistry(image);
        RegistryAuthConfig registryAuth = config.getRegistryAuth(registry);

        if (registryAuth == null) {
            return null;
        }

        AuthConfig authConfig = new AuthConfig()
                .withRegistryAddress(registryAuth.registry())
                .withUsername(registryAuth.username())
                .withPassword(registryAuth.password());

        if (registryAuth.email() != null) {
            authConfig.withEmail(registryAuth.email());
        }

        return authConfig;
    }

    /**
     * Copies source code into a container as a file.
     *
     * <p>The code is packaged as a TAR archive and copied to the container's
     * working directory (configured via {@link DockerExecutionConfig#workingDir()}).
     * The file is created with mode 0644 (rw-r--r--).
     *
     * <p>This method must be called after {@link #createContainer(String, String[])}
     * and before {@link #startContainer(String)}.
     *
     * @param containerId the container ID to copy code into
     * @param code the source code content
     * @param filename the filename to create in the container (e.g., "code.py")
     * @throws IllegalArgumentException if any parameter is blank
     * @throws DockerExecutionException with type {@code CODE_COPY_FAILED} if the copy fails
     */
    public void copyCodeToContainer(String containerId, String code, String filename) {
        ensureNotBlank(containerId, "containerId");
        ensureNotBlank(code, "code");
        ensureNotBlank(filename, "filename");

        LOGGER.debug("Copying code to container {} as {}", containerId, filename);

        try {
            byte[] tarContent = createTarArchive(code, filename);

            dockerClient
                    .copyArchiveToContainerCmd(containerId)
                    .withRemotePath(config.workingDir())
                    .withTarInputStream(new ByteArrayInputStream(tarContent))
                    .exec();

            LOGGER.debug("Successfully copied code to container");
        } catch (IOException | DockerException e) {
            throw DockerExecutionException.codeCopyFailed(e);
        }
    }

    /**
     * Creates a TAR archive containing the code file.
     *
     * <p>The archive contains a single file with the specified name and content.
     * The file is encoded as UTF-8 and given mode 0644 (rw-r--r--).
     *
     * @param code the source code content
     * @param filename the filename within the archive
     * @return the TAR archive as a byte array
     * @throws IOException if archive creation fails
     */
    private byte[] createTarArchive(String code, String filename) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                TarArchiveOutputStream tar = new TarArchiveOutputStream(baos)) {

            byte[] codeBytes = code.getBytes(StandardCharsets.UTF_8);

            TarArchiveEntry entry = new TarArchiveEntry(filename);
            entry.setSize(codeBytes.length);
            entry.setMode(0644); // rw-r--r--

            tar.putArchiveEntry(entry);
            tar.write(codeBytes);
            tar.closeArchiveEntry();
            tar.finish();

            return baos.toByteArray();
        }
    }

    /**
     * Starts a container that was previously created.
     *
     * <p>This begins execution of the container's command. After starting,
     * use {@link #waitForCompletion(String, Duration)} to wait for the
     * container to finish.
     *
     * @param containerId the ID of the container to start
     * @throws IllegalArgumentException if containerId is blank
     * @throws DockerExecutionException if the container fails to start
     */
    public void startContainer(String containerId) {
        ensureNotBlank(containerId, "containerId");

        LOGGER.debug("Starting container: {}", containerId);

        try {
            dockerClient.startContainerCmd(containerId).exec();
            LOGGER.debug("Container started: {}", containerId);
        } catch (DockerException e) {
            throw new DockerExecutionException(
                    DockerExecutionException.ErrorType.UNKNOWN, "Failed to start container: " + containerId, null, e);
        }
    }

    /**
     * Waits for a container to complete execution.
     *
     * <p>This method blocks until the container exits or the timeout is reached.
     * If the timeout is exceeded, the container is forcibly removed and an
     * exception is thrown.
     *
     * @param containerId the ID of the container to wait for
     * @param timeout the maximum time to wait for completion
     * @return the container's exit code (0 typically means success)
     * @throws IllegalArgumentException if containerId is blank or timeout is null
     * @throws DockerExecutionException with type {@code EXECUTION_TIMEOUT} if the timeout is exceeded
     * @throws DockerExecutionException for other Docker API errors
     */
    public int waitForCompletion(String containerId, Duration timeout) {
        ensureNotBlank(containerId, "containerId");
        ensureNotNull(timeout, "timeout");

        LOGGER.debug("Waiting for container {} with timeout {}", containerId, timeout);

        try (WaitContainerResultCallback callback = new WaitContainerResultCallback()) {
            dockerClient.waitContainerCmd(containerId).exec(callback);

            boolean completed = callback.awaitCompletion(timeout.toMillis(), TimeUnit.MILLISECONDS);

            if (!completed) {
                // Timeout - force kill container
                LOGGER.warn("Container {} execution timed out after {} seconds", containerId, timeout.getSeconds());
                forceRemoveContainer(containerId);
                throw DockerExecutionException.executionTimeout(null, timeout.getSeconds());
            }

            Integer statusCode = callback.awaitStatusCode();

            LOGGER.debug("Container {} completed with exit code {}", containerId, statusCode);
            return statusCode != null ? statusCode : -1;
        } catch (DockerExecutionException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            forceRemoveContainer(containerId);
            throw DockerExecutionException.executionTimeout(null, timeout.getSeconds());
        } catch (IOException e) {
            throw new DockerExecutionException(
                    DockerExecutionException.ErrorType.UNKNOWN,
                    "Error closing wait callback for container: " + containerId,
                    null,
                    e);
        } catch (Exception e) {
            throw new DockerExecutionException(
                    DockerExecutionException.ErrorType.UNKNOWN, "Error waiting for container: " + containerId, null, e);
        }
    }

    /**
     * Retrieves the standard output from a container.
     *
     * <p>The output is collected up to the configured maximum size
     * ({@link DockerExecutionConfig#maxOutputSizeBytes()}) to prevent
     * memory exhaustion from excessive output.
     *
     * @param containerId the ID of the container
     * @return the stdout content, possibly truncated
     */
    public String getStdout(String containerId) {
        return getLogs(containerId, StreamType.STDOUT);
    }

    /**
     * Retrieves the standard error output from a container.
     *
     * <p>The output is collected up to the configured maximum size
     * ({@link DockerExecutionConfig#maxOutputSizeBytes()}) to prevent
     * memory exhaustion from excessive output.
     *
     * @param containerId the ID of the container
     * @return the stderr content, possibly truncated
     */
    public String getStderr(String containerId) {
        return getLogs(containerId, StreamType.STDERR);
    }

    /**
     * Retrieves container logs filtered by stream type (stdout or stderr).
     *
     * <p>This method collects logs with a 30-second timeout. Output is truncated
     * at the configured maximum size to prevent memory exhaustion.
     *
     * @param containerId the ID of the container
     * @param streamType the type of stream to collect (STDOUT or STDERR)
     * @return the collected log content
     */
    private String getLogs(String containerId, StreamType streamType) {
        ensureNotBlank(containerId, "containerId");

        StringBuilder output = new StringBuilder();
        int maxSize = config.maxOutputSizeBytes();

        try (ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Frame frame) {
                if (frame.getStreamType() == streamType) {
                    if (output.length() < maxSize) {
                        String payload = new String(frame.getPayload(), StandardCharsets.UTF_8);
                        int remaining = maxSize - output.length();
                        if (payload.length() > remaining) {
                            output.append(payload, 0, remaining);
                        } else {
                            output.append(payload);
                        }
                    }
                }
            }
        }) {
            dockerClient
                    .logContainerCmd(containerId)
                    .withStdOut(streamType == StreamType.STDOUT)
                    .withStdErr(streamType == StreamType.STDERR)
                    .withFollowStream(false)
                    .exec(callback)
                    .awaitCompletion(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while getting logs from container {}", containerId);
        } catch (IOException e) {
            LOGGER.warn("Error closing log callback for container {}: {}", containerId, e.getMessage());
        } catch (DockerException e) {
            LOGGER.warn("Failed to get logs from container {}: {}", containerId, e.getMessage());
        }

        return output.toString();
    }

    /**
     * Retrieves the exit code of a container.
     *
     * <p>This method inspects the container state to get the exit code.
     * It should be called after the container has stopped.
     *
     * @param containerId the ID of the container
     * @return the exit code, or null if not available (e.g., container still running)
     * @throws IllegalArgumentException if containerId is blank
     */
    public Integer getExitCode(String containerId) {
        ensureNotBlank(containerId, "containerId");

        try {
            InspectContainerResponse inspection =
                    dockerClient.inspectContainerCmd(containerId).exec();
            InspectContainerResponse.ContainerState state = inspection.getState();
            if (state != null) {
                return state.getExitCodeLong() != null ? state.getExitCodeLong().intValue() : null;
            }
        } catch (DockerException e) {
            LOGGER.warn("Failed to get exit code for container {}: {}", containerId, e.getMessage());
        }
        return null;
    }

    /**
     * Removes a container gracefully.
     *
     * <p>This method removes a stopped container and its associated volumes.
     * If the container is still running, this method may fail. Use
     * {@link #forceRemoveContainer(String)} for running containers.
     *
     * <p>If the container has already been removed (not found), this method
     * silently succeeds.
     *
     * @param containerId the ID of the container to remove (may be null or empty)
     */
    public void removeContainer(String containerId) {
        if (containerId == null || containerId.isEmpty()) {
            return;
        }

        LOGGER.debug("Removing container: {}", containerId);

        try {
            dockerClient.removeContainerCmd(containerId).withRemoveVolumes(true).exec();
            LOGGER.debug("Removed container: {}", containerId);
        } catch (NotFoundException e) {
            LOGGER.debug("Container already removed: {}", containerId);
        } catch (DockerException e) {
            LOGGER.warn("Failed to remove container {}: {}", containerId, e.getMessage());
        }
    }

    /**
     * Forcibly removes a container, killing it if still running.
     *
     * <p>This method is safe to call on containers in any state. It:
     * <ul>
     *   <li>Kills the container if it's running</li>
     *   <li>Removes the container and its volumes</li>
     *   <li>Silently handles "not found" errors</li>
     * </ul>
     *
     * <p>This is the recommended cleanup method for ensuring containers
     * are always removed, regardless of their execution state.
     *
     * @param containerId the ID of the container to remove (may be null or empty)
     */
    public void forceRemoveContainer(String containerId) {
        if (containerId == null || containerId.isEmpty()) {
            return;
        }

        LOGGER.debug("Force removing container: {}", containerId);

        try {
            dockerClient
                    .removeContainerCmd(containerId)
                    .withForce(true)
                    .withRemoveVolumes(true)
                    .exec();
            LOGGER.debug("Force removed container: {}", containerId);
        } catch (NotFoundException e) {
            LOGGER.debug("Container already removed: {}", containerId);
        } catch (DockerException e) {
            LOGGER.warn("Failed to force remove container {}: {}", containerId, e.getMessage());
        }
    }
}
