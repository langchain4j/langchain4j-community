package dev.langchain4j.community.store.embedding.cockroachdb;

import dev.langchain4j.community.store.embedding.cockroachdb.index.CSpannIndex;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Runs the standard removal test suite from langchain4j-core against CockroachDB.
 * Shares the Testcontainers container declared in {@link CockroachDbTestBase}.
 */
class CockroachDbEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    private static final EmbeddingModel MODEL = new AllMiniLmL6V2QuantizedEmbeddingModel();

    private static CockroachDbEngine engine;
    private static CockroachDbEmbeddingStore store;

    @BeforeAll
    static void initStore() throws SQLException {
        var cockroach = CockroachDbTestBase.cockroach;
        if (!cockroach.isRunning()) cockroach.start();
        engine = CockroachDbEngine.builder()
                .connectionString(cockroach.getJdbcUrl())
                .username(cockroach.getUsername())
                .password(cockroach.getPassword())
                .build();
        try (Connection c = engine.getConnection();
                Statement st = c.createStatement()) {
            st.execute("SET CLUSTER SETTING feature.vector_index.enabled = true");
            st.execute("DROP TABLE IF EXISTS embeddings_removal_it");
        }
        store = CockroachDbEmbeddingStore.builder()
                .engine(engine)
                .dimension(MODEL.dimension())
                .tableName("embeddings_removal_it")
                .vectorIndex(CSpannIndex.builder().build())
                .build();
    }

    @BeforeEach
    void clearBeforeEach() {
        store.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return store;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return MODEL;
    }
}
