package dev.langchain4j.community.model.xinference;

import static dev.langchain4j.community.model.xinference.XinferenceUtils.launchCmd;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.DeviceRequest;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.RuntimeInfo;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

class XinferenceContainer extends GenericContainer<XinferenceContainer> {
    private static final Logger log = LoggerFactory.getLogger(XinferenceContainer.class);
    private static final DockerImageName DOCKER_IMAGE_NAME = DockerImageName.parse("xprobe/xinference");
    private static final Integer EXPOSED_PORT = 9997;
    private String modelName;

    public XinferenceContainer(String image) {
        this(DockerImageName.parse(image));
    }

    public XinferenceContainer(DockerImageName image) {
        super(image);
        image.assertCompatibleWith(DOCKER_IMAGE_NAME);
        Info info = this.dockerClient.infoCmd().exec();
        Map<String, RuntimeInfo> runtimes = info.getRuntimes();
        if (runtimes != null && runtimes.containsKey("nvidia")) {
            this.withCreateContainerCmdModifier((cmd) -> {
                Objects.requireNonNull(cmd.getHostConfig())
                        .withDeviceRequests(Collections.singletonList((new DeviceRequest())
                                .withCapabilities(Collections.singletonList(Collections.singletonList("gpu")))
                                .withCount(-1)));
            });
        }
        this.withExposedPorts(EXPOSED_PORT);
        // https://github.com/xorbitsai/inference/issues/2573
        this.withCommand(
                "bash",
                "-c",
                "pip install tokenizers==0.20.1 transformers==4.45.2 qwen-vl-utils==0.0.8 && xinference-local -H 0.0.0.0");
        this.waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(10)));
    }

    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        if (this.modelName != null) {
            try {
                log.info("Start pulling the '{}' model ... would take several minutes ...", this.modelName);
                ExecResult r = execInContainer("bash", "-c", launchCmd(this.modelName));
                if (r.getExitCode() != 0) {
                    throw new RuntimeException(r.getStderr());
                }
                log.info("Model pulling competed! {}", r);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException("Error pulling model", e);
            }
        }
    }

    public XinferenceContainer withModel(String modelName) {
        this.modelName = modelName;
        return this;
    }

    public void commitToImage(String imageName) {
        DockerImageName dockerImageName = DockerImageName.parse(this.getDockerImageName());
        if (!dockerImageName.equals(DockerImageName.parse(imageName))) {
            DockerClient dockerClient = DockerClientFactory.instance().client();
            List<Image> images =
                    dockerClient.listImagesCmd().withReferenceFilter(imageName).exec();
            if (images.isEmpty()) {
                DockerImageName imageModel = DockerImageName.parse(imageName);
                dockerClient
                        .commitCmd(this.getContainerId())
                        .withRepository(imageModel.getUnversionedPart())
                        .withLabels(Collections.singletonMap("org.testcontainers.sessionId", ""))
                        .withTag(imageModel.getVersionPart())
                        .exec();
            }
        }
    }

    public String getEndpoint() {
        return "http://" + this.getHost() + ":" + this.getMappedPort(EXPOSED_PORT);
    }
}
