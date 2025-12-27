package dev.langchain4j.community.code.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Assumptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test utilities for Docker-based tests.
 * Provides helpers to check Docker availability and skip tests when Docker is not accessible.
 */
public final class DockerTestUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(DockerTestUtils.class);

    private static volatile Boolean dockerAvailable;

    private DockerTestUtils() {
        // Utility class
    }

    /** Checks if Docker daemon is available. Result is cached after first call. */
    public static boolean isDockerAvailable() {
        if (dockerAvailable == null) {
            synchronized (DockerTestUtils.class) {
                if (dockerAvailable == null) {
                    dockerAvailable = checkDockerAvailability();
                }
            }
        }
        return dockerAvailable;
    }

    /** Skips the test if Docker is not available. */
    public static void assumeDockerAvailable() {
        Assumptions.assumeTrue(isDockerAvailable(), "Docker is not available - skipping test");
    }

    /** Creates a Docker client for testing purposes. */
    public static DockerClient createDockerClient() {
        DockerClientConfig config =
                DefaultDockerClientConfig.createDefaultConfigBuilder().build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(10)
                .connectionTimeout(Duration.ofSeconds(10))
                .responseTimeout(Duration.ofSeconds(30))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }

    /** Pulls a Docker image if not already present locally. */
    public static void ensureImageAvailable(String imageName) {
        try (DockerClient client = createDockerClient()) {
            // Check if image exists locally
            boolean imageExists = client.listImagesCmd().withImageNameFilter(imageName).exec().stream()
                    .anyMatch(image -> image.getRepoTags() != null
                            && java.util.Arrays.asList(image.getRepoTags()).contains(imageName));

            if (!imageExists) {
                LOGGER.info("Pulling image: {}", imageName);
                client.pullImageCmd(imageName).start().awaitCompletion();
                LOGGER.info("Successfully pulled image: {}", imageName);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to ensure image availability: {} - {}", imageName, e.getMessage());
        }
    }

    private static boolean checkDockerAvailability() {
        try (DockerClient client = createDockerClient()) {
            client.pingCmd().exec();
            LOGGER.debug("Docker is available");
            return true;
        } catch (Exception e) {
            LOGGER.info("Docker is not available: {}", e.getMessage());
            return false;
        }
    }
}
