package dev.langchain4j.community.store.embedding.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import com.hazelcast.config.vector.Metric;
import com.hazelcast.config.vector.VectorCollectionConfig;
import com.hazelcast.config.vector.VectorIndexConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.vector.VectorCollection;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import dev.langchain4j.store.embedding.filter.Filter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Runs the standard {@link EmbeddingStoreIT} contract suite against {@link HazelcastEmbeddingStore},
 * plus a few builder/factory tests not covered by the shared suite.
 * <p>
 * Requires a Hazelcast Enterprise license key in the {@code HZ_LICENSEKEY} environment variable;
 * the class is skipped (e.g. in CI) when it is not set. An embedded single-member cluster is used —
 * no external process needed.
 */
@EnabledIfEnvironmentVariable(named = "HZ_LICENSEKEY", matches = ".+")
class HazelcastEmbeddingStoreIT extends EmbeddingStoreIT {

    /** Dimension of {@link AllMiniLmL6V2QuantizedEmbeddingModel}. */
    private static final int DIMENSION = 384;

    static HazelcastInstance hazelcastInstance;

    static EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    static HazelcastEmbeddingStore embeddingStore;

    @BeforeAll
    static void startHazelcast() {
        Config config = new Config();
        config.setClusterName("langchain4j-embedding-it");
        config.setLicenseKey(System.getenv("HZ_LICENSEKEY"));
        JoinConfig join = config.getNetworkConfig().getJoin();
        join.getMulticastConfig().setEnabled(false);
        join.getTcpIpConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);

        embeddingStore = HazelcastEmbeddingStore.builder()
                .hazelcastInstance(hazelcastInstance)
                .collectionName("embedding-store-it")
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

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        embeddingStore.removeAll();
    }

    // -------------------------------------------------------------------------
    // Builder / factory behaviour not covered by the shared suite
    // -------------------------------------------------------------------------

    @Test
    void builder_throws_when_hazelcast_instance_missing() {
        assertThatThrownBy(() -> HazelcastEmbeddingStore.builder()
                        .collectionName("missing-instance")
                        .dimension(DIMENSION)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hazelcastInstance");
    }

    @Test
    void builder_throws_when_dimension_not_set() {
        assertThatThrownBy(() -> HazelcastEmbeddingStore.builder()
                        .hazelcastInstance(hazelcastInstance)
                        .collectionName("missing-dimension")
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dimension");
    }

    @Test
    void removeAll_filter_throws_unsupported() {
        assertThatThrownBy(() -> embeddingStore.removeAll((Filter) null))
                .isInstanceOf(UnsupportedFeatureException.class);
    }

    @Test
    void should_accept_pre_configured_collection() {
        VectorCollectionConfig config = new VectorCollectionConfig("pre-configured-it")
                .addVectorIndexConfig(
                        new VectorIndexConfig().setDimension(DIMENSION).setMetric(Metric.COSINE));
        VectorCollection<String, TextSegmentDocument> col = VectorCollection.getCollection(hazelcastInstance, config);

        HazelcastEmbeddingStore store = HazelcastEmbeddingStore.create(col);
        String id = store.add(embeddingModel.embed("pre-configured").content(), TextSegment.from("pre-configured"));
        assertThat(id).isNotBlank();

        col.destroy();
    }
}
