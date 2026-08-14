package dev.langchain4j.community.store.embedding.cockroachdb;

import dev.langchain4j.community.store.embedding.cockroachdb.index.CSpannIndex;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeAll;

/**
 * Runs the standard LangChain4j filtering test suite against CockroachDB.
 * Extends {@link CockroachDbTestBase} for the Testcontainers setup and
 * {@link EmbeddingStoreWithFilteringIT} for the test methods themselves.
 */
class CockroachDbEmbeddingStoreIT extends EmbeddingStoreWithFilteringIT {

    private static final EmbeddingModel MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private static CockroachDbEngine engine;
    private static CockroachDbEmbeddingStore store;

    @BeforeAll
    static void initStore() throws SQLException {
        org.testcontainers.containers.CockroachContainer cockroach = CockroachDbTestBase.cockroach;
        if (!cockroach.isRunning()) cockroach.start();
        engine = CockroachDbEngine.builder()
                .connectionString(cockroach.getJdbcUrl())
                .username(cockroach.getUsername())
                .password(cockroach.getPassword())
                .build();
        try (Connection c = engine.getConnection();
                Statement st = c.createStatement()) {
            // CockroachDB v25.2 hides the C-SPANN vector index behind a cluster setting.
            st.execute("SET CLUSTER SETTING feature.vector_index.enabled = true");
            st.execute("DROP TABLE IF EXISTS embeddings_it");
        }
        store = CockroachDbEmbeddingStore.builder()
                .engine(engine)
                .dimension(MODEL.dimension())
                .tableName("embeddings_it")
                .vectorIndex(CSpannIndex.builder().build())
                .build();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return store;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return MODEL;
    }

    @Override
    protected void clearStore() {
        store.removeAll();
    }
}
