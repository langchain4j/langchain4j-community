package dev.langchain4j.community.store.embedding.vearch;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;
import org.testcontainers.utility.MountableFile;

public class VearchContainer extends GenericContainer<VearchContainer> {

    public VearchContainer() {
        super("vearch/vearch:latest");
        withExposedPorts(9001, 8817);
        withCommand("all");
        withCopyFileToContainer(MountableFile.forClasspathResource("config.toml"), "/vearch/config.toml");
        withStartupCheckStrategy(new MinimumDurationRunningStartupCheckStrategy(Duration.ofSeconds(10)));
    }
}
