package dev.langchain4j.community.store.embedding.arcadedb;

import com.arcadedb.Constants;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * ArcadeDB server container for the remote-mode ITs. The image tag is the version of the ArcadeDB client jars on the
 * test classpath, so remote mode is always tested against the server version it is built with, including when the
 * build overrides {@code -Darcadedb.version}.
 */
final class ArcadeDBTestServer {

    static final int HTTP_PORT = 2480;
    static final String ROOT_PASSWORD = "playwithdata";
    static final String IMAGE = "arcadedata/arcadedb:" + Constants.getRawVersion();

    private ArcadeDBTestServer() {}

    static GenericContainer<?> newContainer() {
        return new GenericContainer<>(IMAGE)
                .withExposedPorts(HTTP_PORT)
                .withEnv("JAVA_OPTS", "-Darcadedb.server.rootPassword=" + ROOT_PASSWORD)
                .waitingFor(Wait.forHttp("/api/v1/ready").forPort(HTTP_PORT).forStatusCode(204));
    }
}
