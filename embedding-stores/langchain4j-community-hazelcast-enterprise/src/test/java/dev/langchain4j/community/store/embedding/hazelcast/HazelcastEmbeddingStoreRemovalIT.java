package dev.langchain4j.community.store.embedding.hazelcast;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.vector.Metric;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Runs the standard {@link EmbeddingStoreWithRemovalIT} contract suite against
 * {@link HazelcastEmbeddingStore}.
 * <p>
 * Hazelcast {@link com.hazelcast.vector.VectorCollection} has no server-side predicate delete, so
 * {@link #supportsRemoveAllByFilter()} is disabled; {@code removeAll()} (clear all) is supported.
 * <p>
 * Requires a Hazelcast Enterprise license key in the {@code HZ_LICENSEKEY} environment variable;
 * the class is skipped (e.g. in CI) when it is not set.
 */
@EnabledIfEnvironmentVariable(named = "HZ_LICENSEKEY", matches = ".+")
class HazelcastEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    /** Dimension of {@link AllMiniLmL6V2QuantizedEmbeddingModel}. */
    private static final int DIMENSION = 384;

    static HazelcastInstance hazelcastInstance;

    static EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    static HazelcastEmbeddingStore embeddingStore;

    @BeforeAll
    static void startHazelcast() {
        Config config = new Config();
        config.setClusterName("langchain4j-embedding-removal-it");
        config.setLicenseKey(System.getenv("HZ_LICENSEKEY"));
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);

        embeddingStore = HazelcastEmbeddingStore.builder()
                .hazelcastInstance(hazelcastInstance)
                .collectionName("embedding-store-removal-it")
                .dimension(DIMENSION)
                .metric(Metric.COSINE)
                .build();
    }

    @AfterAll
    static void stopHazelcast() {
        if (hazelcastInstance != null) {
            hazelcastInstance.shutdown();
        }
    }

    @BeforeEach
    void clearEmbeddings() {
        embeddingStore.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected boolean supportsRemoveAllByFilter() {
        return false; // Hazelcast VectorCollection has no server-side predicate delete
    }
}
