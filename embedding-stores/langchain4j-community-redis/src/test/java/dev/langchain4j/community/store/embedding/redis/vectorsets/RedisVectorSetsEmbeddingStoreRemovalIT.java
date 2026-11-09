package dev.langchain4j.community.store.embedding.redis.vectorsets;

import com.redis.testcontainers.RedisContainer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.UnifiedJedis;

import java.util.UUID;

class RedisVectorSetsEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    private static final String KEY = UUID.randomUUID().toString();

    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:8-alpine"));

    private final UnifiedJedis redisClient;
    private final EmbeddingModel embeddingModel;
    private final RedisVectorSetsEmbeddingStore embeddingStore;

    public RedisVectorSetsEmbeddingStoreRemovalIT() {
        redisClient = new UnifiedJedis(new HostAndPort(redis.getRedisHost(), redis.getMappedPort(6379)));
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
        embeddingStore = new RedisVectorSetsEmbeddingStore(redisClient, KEY);
    }

    @BeforeAll
    static void beforeAll() {
        redis.start();
    }

    @AfterAll
    static void afterAll() {
        redis.stop();
    }

    @AfterEach
    void afterEach() {
        redisClient.del(KEY);
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
        return false;
    }
}
