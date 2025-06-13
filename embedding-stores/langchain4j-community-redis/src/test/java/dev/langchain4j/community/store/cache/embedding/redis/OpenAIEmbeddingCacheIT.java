package dev.langchain4j.community.store.cache.embedding.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.JedisPooled;

/**
 * Integration test for RedisEmbeddingCache with OpenAI embedding model.
 * This test requires Docker to run Redis in a container and an OpenAI API key.
 */
@Testcontainers
public class OpenAIEmbeddingCacheIT {

    private static final int REDIS_PORT = 6379;

    @Container
    private final GenericContainer<?> redis =
            new GenericContainer<>("redis/redis-stack:latest").withExposedPorts(REDIS_PORT);

    private JedisPooled jedisPooled;
    private RedisEmbeddingCache embeddingCache;
    private EmbeddingModel embeddingModel;
    private CachedEmbeddingModel cachedModel;

    @BeforeEach
    public void setUp() {
        String openAiApiKey = System.getenv("OPENAI_API_KEY");
        assumeTrue(
                openAiApiKey != null && !openAiApiKey.isEmpty(),
                "OpenAI API key not found in environment variables. Skipping test.");

        String host = redis.getHost();
        Integer port = redis.getMappedPort(REDIS_PORT);

        jedisPooled = new JedisPooled(host, port);

        // Use OpenAI embedding model for real-world testing
        embeddingModel = OpenAiEmbeddingModel.builder()
                .apiKey(openAiApiKey)
                .modelName("text-embedding-3-small")
                .dimensions(1536)
                .build();

        // Create the Redis embedding cache
        embeddingCache = RedisEmbeddingCacheBuilder.builder()
                .redis(jedisPooled)
                .keyPrefix("openai-embedding-cache")
                .ttlSeconds(60)
                .maxCacheSize(100)
                .build();

        // Create the cached model with our cache
        cachedModel = CachedEmbeddingModelBuilder.builder()
                .delegate(embeddingModel)
                .cache(embeddingCache)
                .build();
    }

    @AfterEach
    public void tearDown() {
        if (embeddingCache != null) {
            embeddingCache.clear();
        }
    }

    @Test
    public void should_cache_and_retrieve_openai_embeddings() {
        // given
        String text = "This is a test text for OpenAI embedding";

        // First call - should compute and cache (will call OpenAI API)
        Response<Embedding> firstResponse = cachedModel.embed(text);

        // Verify first response
        assertThat(firstResponse).isNotNull();
        assertThat(firstResponse.content()).isNotNull();
        assertThat(firstResponse.content().vector()).isNotNull();

        // Verify vector dimension matches OpenAI's model dimension
        assertThat(firstResponse.content().vector().length).isEqualTo(1536);

        // Second call - should retrieve from cache (no API call)
        Response<Embedding> secondResponse = cachedModel.embed(text);

        // Verify second response matches first
        assertThat(secondResponse).isNotNull();
        assertThat(secondResponse.content()).isNotNull();
        assertThat(secondResponse.content().vector()).isNotNull();

        // Verify exact same vector is returned (indicating cache hit)
        assertThat(secondResponse.content().vector())
                .isEqualTo(firstResponse.content().vector());
    }

    @Test
    public void should_cache_and_retrieve_batch_openai_embeddings() {
        // given
        List<TextSegment> segments = Arrays.asList(
                TextSegment.from("First OpenAI test segment"),
                TextSegment.from("Second OpenAI test segment"),
                TextSegment.from("Third OpenAI test segment"));

        // First batch call - should compute and cache (will call OpenAI API)
        Response<List<Embedding>> firstResponse = cachedModel.embedAll(segments);

        // Verify first response
        assertThat(firstResponse).isNotNull();
        assertThat(firstResponse.content()).isNotNull();
        assertThat(firstResponse.content()).hasSize(3);

        // Verify token usage information is available (OpenAI specific)
        assertThat(firstResponse.tokenUsage()).isNotNull();

        // Store vectors for comparison
        float[] firstVector = firstResponse.content().get(0).vector();
        float[] secondVector = firstResponse.content().get(1).vector();
        float[] thirdVector = firstResponse.content().get(2).vector();

        // Second batch call - should retrieve from cache (no API call)
        Response<List<Embedding>> secondResponse = cachedModel.embedAll(segments);

        // Verify second response has same vectors (indicating cache hit)
        assertThat(secondResponse.content().get(0).vector()).isEqualTo(firstVector);
        assertThat(secondResponse.content().get(1).vector()).isEqualTo(secondVector);
        assertThat(secondResponse.content().get(2).vector()).isEqualTo(thirdVector);
    }

    @Test
    public void should_handle_mixed_cache_hits_and_misses() {
        // Pre-cache some embeddings
        List<TextSegment> initialSegments =
                Arrays.asList(TextSegment.from("Cached segment one"), TextSegment.from("Cached segment two"));

        cachedModel.embedAll(initialSegments);

        // Create a mixed batch of cached and uncached segments
        List<TextSegment> mixedSegments = Arrays.asList(
                TextSegment.from("Cached segment one"), // Should be a cache hit
                TextSegment.from("Brand new segment"), // Should be a cache miss
                TextSegment.from("Cached segment two")); // Should be a cache hit

        // Process the mixed batch
        Response<List<Embedding>> response = cachedModel.embedAll(mixedSegments);

        // Verify response
        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(3);

        // Verify that calling again gives the same vectors (indicating they're all cached now)
        Response<List<Embedding>> secondResponse = cachedModel.embedAll(mixedSegments);

        for (int i = 0; i < 3; i++) {
            assertThat(secondResponse.content().get(i).vector())
                    .isEqualTo(response.content().get(i).vector());
        }
    }
}
