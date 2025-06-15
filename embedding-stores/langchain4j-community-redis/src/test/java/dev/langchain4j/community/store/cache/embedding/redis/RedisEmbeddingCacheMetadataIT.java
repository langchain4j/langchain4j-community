package dev.langchain4j.community.store.cache.embedding.redis;

import static org.assertj.core.api.Assertions.assertThat;

import com.redis.testcontainers.RedisStackContainer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.JedisPooled;

/**
 * Integration test for Redis Embedding Cache with metadata functionality.
 * Uses TestContainers to run a Redis Stack instance.
 */
@Testcontainers
class RedisEmbeddingCacheMetadataIT {

    @Container
    private final RedisStackContainer redis =
            new RedisStackContainer(DockerImageName.parse("redis/redis-stack:latest"));

    private JedisPooled jedis;
    private EmbeddingModel embeddingModel;
    private RedisEmbeddingCache embeddingCache;

    @BeforeEach
    void setUp() {
        // Connect to Redis in the container
        jedis = new JedisPooled(redis.getHost(), redis.getFirstMappedPort());

        // Create an embedding model
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

        // Create a Redis embedding cache
        embeddingCache = RedisEmbeddingCache.builder()
                .redis(jedis)
                .keyPrefix("metadata-test-cache")
                .ttlSeconds(3600) // 1 hour TTL
                .maxCacheSize(10000)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (embeddingCache != null) {
            embeddingCache.clear();
        }
        if (jedis != null) {
            jedis.close();
        }
    }

    @Test
    void shouldStoreAndRetrieveEmbeddingWithMetadata() {
        // Given
        String text = "This is a sample text for embedding with metadata";

        // Create metadata for the embedding
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "example");
        metadata.put("category", "documentation");
        metadata.put("timestamp", Instant.now().toEpochMilli());
        metadata.put("version", 1.0);
        metadata.put("tags", Arrays.asList("sample", "embedding", "metadata"));

        // When: Generate embedding and cache it with metadata
        Response<Embedding> response = embeddingModel.embed(text);
        embeddingCache.put(text, response.content(), metadata);

        // Then: Retrieve embedding with metadata
        Optional<Map.Entry<Embedding, Map<String, Object>>> result = embeddingCache.getWithMetadata(text);

        assertThat(result).isPresent();
        Map.Entry<Embedding, Map<String, Object>> entry = result.get();
        Embedding embedding = entry.getKey();
        Map<String, Object> retrievedMetadata = entry.getValue();

        // Verify all metadata was stored correctly
        assertThat(embedding.vector()).isNotNull().hasSize(embeddingModel.dimension());
        assertThat(retrievedMetadata)
                .containsEntry("source", "example")
                .containsEntry("category", "documentation")
                .containsEntry("version", 1.0)
                .containsKey("timestamp");

        // Verify complex types like lists
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) retrievedMetadata.get("tags");
        assertThat(tags).containsExactly("sample", "embedding", "metadata");
    }

    @Test
    void shouldFindEmbeddingsByMetadata() {
        // Given: Add sample embeddings with different metadata
        addSampleEmbeddingsWithMetadata();

        // When: Find all embeddings in science category
        Map<String, Object> scienceFilter = new HashMap<>();
        scienceFilter.put("category", "science");

        Map<String, Map.Entry<Embedding, Map<String, Object>>> scienceResults =
                embeddingCache.findByMetadata(scienceFilter, 10);

        // Then: Verify we found the right number of embeddings
        assertThat(scienceResults).hasSize(3);

        // When: Find all important embeddings
        Map<String, Object> importantFilter = new HashMap<>();
        importantFilter.put("important", true);

        Map<String, Map.Entry<Embedding, Map<String, Object>>> importantResults =
                embeddingCache.findByMetadata(importantFilter, 10);

        // Then: Verify we found all important embeddings
        assertThat(importantResults).hasSize(5);

        // Verify a specific embedding in the results
        assertThat(importantResults).containsKey("DNA is the building block of life");
        Map.Entry<Embedding, Map<String, Object>> dnaEntry = importantResults.get("DNA is the building block of life");

        assertThat(dnaEntry.getValue()).containsEntry("category", "science").containsEntry("important", true);
    }

    @Test
    void shouldPerformBatchOperationsWithMetadata() {
        // Given: Batch of embeddings with metadata
        Map<String, Map.Entry<Embedding, Map<String, Object>>> batchEmbs = new HashMap<>();

        // Create several embeddings with metadata
        for (int i = 0; i < 5; i++) {
            String batchText = "Batch text example " + i;
            Response<Embedding> batchResp = embeddingModel.embed(batchText);

            Map<String, Object> batchMeta = new HashMap<>();
            batchMeta.put("index", i);
            batchMeta.put("batch", "example");
            batchMeta.put("even", (i % 2 == 0));

            batchEmbs.put(batchText, Map.entry(batchResp.content(), batchMeta));
        }

        // When: Store all in one batch operation
        embeddingCache.putWithMetadata(batchEmbs);

        // And: Retrieve all in one batch operation
        List<String> batchTexts = Arrays.asList(
                "Batch text example 0",
                "Batch text example 1",
                "Batch text example 2",
                "Batch text example 3",
                "Batch text example 4");

        Map<String, Map.Entry<Embedding, Map<String, Object>>> batchResults =
                embeddingCache.getWithMetadata(batchTexts);

        // Then: Verify all embeddings were retrieved with correct metadata
        assertThat(batchResults).hasSize(5);

        // Verify specific properties
        Map.Entry<Embedding, Map<String, Object>> item0 = batchResults.get("Batch text example 0");
        Map.Entry<Embedding, Map<String, Object>> item1 = batchResults.get("Batch text example 1");

        assertThat(item0.getValue())
                .containsEntry("index", 0)
                .containsEntry("batch", "example")
                .containsEntry("even", true);

        assertThat(item1.getValue())
                .containsEntry("index", 1)
                .containsEntry("batch", "example")
                .containsEntry("even", false);

        // Verify all even-indexed items have "even": true
        assertThat(batchResults.get("Batch text example 0").getValue()).containsEntry("even", true);
        assertThat(batchResults.get("Batch text example 2").getValue()).containsEntry("even", true);
        assertThat(batchResults.get("Batch text example 4").getValue()).containsEntry("even", true);

        // Verify all odd-indexed items have "even": false
        assertThat(batchResults.get("Batch text example 1").getValue()).containsEntry("even", false);
        assertThat(batchResults.get("Batch text example 3").getValue()).containsEntry("even", false);
    }

    private void addSampleEmbeddingsWithMetadata() {
        // Science category
        addEmbeddingWithCategory("DNA is the building block of life", "science", true);
        addEmbeddingWithCategory("Quantum physics deals with subatomic particles", "science", true);
        addEmbeddingWithCategory("The theory of relativity changed physics forever", "science", true);

        // History category
        addEmbeddingWithCategory("The Roman Empire fell in 476 CE", "history", false);
        addEmbeddingWithCategory("World War II ended in 1945", "history", true);
        addEmbeddingWithCategory("The Renaissance was a period of cultural rebirth", "history", false);

        // Art category
        addEmbeddingWithCategory("The Mona Lisa was painted by Leonardo da Vinci", "art", true);
        addEmbeddingWithCategory("Cubism was pioneered by Pablo Picasso", "art", false);
        addEmbeddingWithCategory("Impressionism began in the late 19th century", "art", false);
    }

    private void addEmbeddingWithCategory(String text, String category, boolean important) {
        Response<Embedding> response = embeddingModel.embed(text);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("category", category);
        metadata.put("important", important);
        metadata.put("added", Instant.now().toEpochMilli());

        embeddingCache.put(text, response.content(), metadata);
    }
}
