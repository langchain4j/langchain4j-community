package dev.langchain4j.community.code.docker;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.async.ResultCallback;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages Docker container lifecycle: create, copy code, start, wait, get logs, remove.
 */
public class DockerContainerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerContainerManager.class);

    private final DockerClient dockerClient;
    private final DockerExecutionConfig config;

    /** Creates a new container manager. */
    public DockerContainerManager(DockerClient dockerClient, DockerExecutionConfig config) {
        this.dockerClient = ensureNotNull(dockerClient, "dockerClient");
        this.config = ensureNotNull(config, "config");
    }

    /** Creates a container with the specified image and command. Returns container ID. */
    public String createContainer(String image, String[] command) {
        ensureNotBlank(image, "image");
        ensureNotNull(command, "command");

        LOGGER.debug("Creating container with image: {}, command: {}", image, Arrays.toString(command));

        try {
            // Ensure image is available
            pullImageIfNeeded(image);

            // Build host configuration with security constraints
            HostConfig hostConfig = buildHostConfig();

            // Create container
            CreateContainerResponse container = dockerClient.createContainerCmd(image)
                    .withHostConfig(hostConfig)
                    .withCmd(command)
                    .withWorkingDir(config.workingDir())
                    .withNetworkDisabled(config.networkDisabled())
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

    /** Builds host configuration with security constraints. */
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

    /** Parses capability strings into Capability enum values. */
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

    /** Pulls the image if not available locally. */
    private void pullImageIfNeeded(String image) {
        try {
            // Check if image exists locally
            dockerClient.inspectImageCmd(image).exec();
            LOGGER.debug("Image {} already available locally", image);
        } catch (NotFoundException e) {
            // Image not found locally, pull it
            LOGGER.info("Pulling image: {}", image);
            try {
                dockerClient.pullImageCmd(image)
                        .start()
                        .awaitCompletion(5, TimeUnit.MINUTES);
                LOGGER.debug("Successfully pulled image: {}", image);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw DockerExecutionException.imagePullFailed(image, ie);
            } catch (DockerException de) {
                throw DockerExecutionException.imagePullFailed(image, de);
            }
        }
    }

    /** Copies code to the container as a file. */
    public void copyCodeToContainer(String containerId, String code, String filename) {
        ensureNotBlank(containerId, "containerId");
        ensureNotBlank(code, "code");
        ensureNotBlank(filename, "filename");

        LOGGER.debug("Copying code to container {} as {}", containerId, filename);

        try {
            byte[] tarContent = createTarArchive(code, filename);

            dockerClient.copyArchiveToContainerCmd(containerId)
                    .withRemotePath(config.workingDir())
                    .withTarInputStream(new ByteArrayInputStream(tarContent))
                    .exec();

            LOGGER.debug("Successfully copied code to container");
        } catch (IOException | DockerException e) {
            throw DockerExecutionException.codeCopyFailed(e);
        }
    }

    /** Creates a tar archive containing the code file. */
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

    /** Starts a container. */
    public void startContainer(String containerId) {
        ensureNotBlank(containerId, "containerId");

        LOGGER.debug("Starting container: {}", containerId);

        try {
            dockerClient.startContainerCmd(containerId).exec();
            LOGGER.debug("Container started: {}", containerId);
        } catch (DockerException e) {
            throw new DockerExecutionException(
                    DockerExecutionException.ErrorType.UNKNOWN,
                    "Failed to start container: " + containerId,
                    null,
                    e
            );
        }
    }

    /** Waits for container to complete. Returns exit code. */
    public int waitForCompletion(String containerId, Duration timeout) {
        ensureNotBlank(containerId, "containerId");
        ensureNotNull(timeout, "timeout");

        LOGGER.debug("Waiting for container {} with timeout {}", containerId, timeout);

        try {
            WaitContainerResultCallback callback = new WaitContainerResultCallback();
            dockerClient.waitContainerCmd(containerId).exec(callback);

            Integer statusCode = callback.awaitStatusCode(timeout.toMillis(), TimeUnit.MILLISECONDS);

            if (statusCode == null) {
                // Timeout - force kill container
                forceRemoveContainer(containerId);
                throw DockerExecutionException.executionTimeout(null, timeout.getSeconds());
            }

            LOGGER.debug("Container {} completed with exit code {}", containerId, statusCode);
            return statusCode;
        } catch (DockerExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new DockerExecutionException(
                    DockerExecutionException.ErrorType.UNKNOWN,
                    "Error waiting for container: " + containerId,
                    null,
                    e
            );
        }
    }

    /** Gets stdout from a container. */
    public String getStdout(String containerId) {
        return getLogs(containerId, StreamType.STDOUT);
    }

    /** Gets stderr from a container. */
    public String getStderr(String containerId) {
        return getLogs(containerId, StreamType.STDERR);
    }

    /** Gets logs filtered by stream type. */
    private String getLogs(String containerId, StreamType streamType) {
        ensureNotBlank(containerId, "containerId");

        StringBuilder output = new StringBuilder();
        int maxSize = config.maxOutputSizeBytes();

        try {
            ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
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
            };

            dockerClient.logContainerCmd(containerId)
                    .withStdOut(streamType == StreamType.STDOUT)
                    .withStdErr(streamType == StreamType.STDERR)
                    .withFollowStream(false)
                    .exec(callback)
                    .awaitCompletion(30, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while getting logs from container {}", containerId);
        } catch (DockerException e) {
            LOGGER.warn("Failed to get logs from container {}: {}", containerId, e.getMessage());
        }

        return output.toString();
    }

    /** Gets exit code, or null if not available. */
    public Integer getExitCode(String containerId) {
        ensureNotBlank(containerId, "containerId");

        try {
            InspectContainerResponse inspection = dockerClient.inspectContainerCmd(containerId).exec();
            InspectContainerResponse.ContainerState state = inspection.getState();
            if (state != null) {
                return state.getExitCodeLong() != null ? state.getExitCodeLong().intValue() : null;
            }
        } catch (DockerException e) {
            LOGGER.warn("Failed to get exit code for container {}: {}", containerId, e.getMessage());
        }
        return null;
    }

    /** Removes a container. */
    public void removeContainer(String containerId) {
        if (containerId == null || containerId.isEmpty()) {
            return;
        }

        LOGGER.debug("Removing container: {}", containerId);

        try {
            dockerClient.removeContainerCmd(containerId)
                    .withRemoveVolumes(true)
                    .exec();
            LOGGER.debug("Removed container: {}", containerId);
        } catch (NotFoundException e) {
            LOGGER.debug("Container already removed: {}", containerId);
        } catch (DockerException e) {
            LOGGER.warn("Failed to remove container {}: {}", containerId, e.getMessage());
        }
    }

    /** Force removes a container (kills if running). */
    public void forceRemoveContainer(String containerId) {
        if (containerId == null || containerId.isEmpty()) {
            return;
        }

        LOGGER.debug("Force removing container: {}", containerId);

        try {
            dockerClient.removeContainerCmd(containerId)
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
