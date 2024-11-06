package dev.langchain4j.community.web.search.searxng;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchEngineIT;

@Testcontainers
class SearXNGWebSearchEngineIT extends WebSearchEngineIT {
	@SuppressWarnings("resource")
	@Container
	static GenericContainer<?> searxng = new GenericContainer<>(DockerImageName.parse("searxng/searxng:latest"))
            .withExposedPorts(8080)
            .withCopyFileToContainer(MountableFile.forClasspathResource("settings.yml"), "/usr/local/searxng/searx/settings.yml")
            .waitingFor(Wait.forLogMessage(".*spawned uWSGI worker.*\\n", 1));
	
    @Override
    protected WebSearchEngine searchEngine() {
    	return SearXNGWebSearchEngine.builder().baseUrl("http://" + searxng.getHost() + ":" + searxng.getMappedPort(8080)).build();
    }
    
    @BeforeAll
    static void startContainers() {
      searxng.start();
    }

    @AfterAll
    static void stopContainers() {
       	searxng.stop();
       	searxng.close();
    }
}
