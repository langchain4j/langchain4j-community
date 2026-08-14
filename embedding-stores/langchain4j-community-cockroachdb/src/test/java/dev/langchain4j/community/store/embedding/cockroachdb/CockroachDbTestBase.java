package dev.langchain4j.community.store.embedding.cockroachdb;

import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.CockroachContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers setup. Uses a single-node CockroachDB v25.2+ image so the
 * VECTOR type and {@code CREATE VECTOR INDEX} DDL are available.
 */
@Testcontainers
public abstract class CockroachDbTestBase {

    public static final DockerImageName IMAGE = DockerImageName.parse("cockroachdb/cockroach:latest-v25.2");

    @Container
    @SuppressWarnings("resource")
    public static final CockroachContainer cockroach =
            new CockroachContainer(IMAGE).waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(3)));

    private static final Logger logger = LoggerFactory.getLogger(CockroachDbTestBase.class);
    protected static CockroachDbEngine engine;

    @BeforeAll
    static void initEngine() throws java.sql.SQLException {
        logger.info("CockroachDB container: {}", cockroach.getJdbcUrl());
        engine = CockroachDbEngine.builder()
                .connectionString(cockroach.getJdbcUrl())
                .username(cockroach.getUsername())
                .password(cockroach.getPassword())
                .build();
        // CockroachDB v25.2 hides the C-SPANN vector index behind a cluster setting.
        try (java.sql.Connection c = engine.getConnection();
                java.sql.Statement st = c.createStatement()) {
            st.execute("SET CLUSTER SETTING feature.vector_index.enabled = true");
        }
    }

    @AfterAll
    static void closeEngine() {
        if (engine != null) engine.close();
    }
}
