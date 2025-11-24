package dev.langchain4j.community.store.embedding.redis.vectorsets;

import com.redis.testcontainers.RedisContainer;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithFilteringIT;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.VAddParams;

import java.util.UUID;

class RedisVectorSetsEmbeddingStoreWithFilteringIT extends EmbeddingStoreWithFilteringIT {

    private static final String KEY = UUID.randomUUID().toString();

    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:8-alpine"));

    Jedis redisClient = new Jedis(redis.getRedisHost(), redis.getMappedPort(6379));
    EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();
    RedisVectorSetsEmbeddingStore embeddingStore = new RedisVectorSetsEmbeddingStore(redisClient, KEY, new SimilarityFilterMapper(), null, () -> new VAddParams().noQuant());

    @BeforeAll
    static void beforeAll() {
        redis.start();
    }

    @AfterAll
    static void afterAll() {
        redis.stop();
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
    protected boolean assertEmbedding() {
        return false;
    }

    @Override
    protected void clearStore() {
        super.clearStore();
        redisClient.del(KEY);
    }
}
