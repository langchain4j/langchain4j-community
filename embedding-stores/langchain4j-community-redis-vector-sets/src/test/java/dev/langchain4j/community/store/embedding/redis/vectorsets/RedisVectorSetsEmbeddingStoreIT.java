package dev.langchain4j.community.store.embedding.redis.vectorsets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redis.testcontainers.RedisContainer;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsIn;
import dev.langchain4j.store.embedding.filter.comparison.IsLessThanOrEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsNotIn;
import org.junit.jupiter.api.Test;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.Jedis;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RedisVectorSetsEmbeddingStoreIT implements AutoCloseable {

    private final EmbeddingStore<TextSegment> store;
    private final RedisContainer redis;
    private final Jedis redisClient;
    private final EmbeddingModel embeddingModel;
    private final ObjectMapper objectMapper;


    public RedisVectorSetsEmbeddingStoreIT() throws JsonProcessingException {
        redis = new RedisContainer(DockerImageName.parse("redis:8-alpine"));
        redis.start();
        redisClient = new Jedis(redis.getRedisHost(), redis.getMappedPort(6379));
        store = new RedisVectorSetsEmbeddingStore(redisClient, "sentences");

        final Function<String, Sentence> jacopoSays = text -> new Sentence("jacopo", text, 30);
        final Function<String, Sentence> bobSays = text -> new Sentence("bob", text, 25);

        objectMapper = new ObjectMapper();

        var randomSentences = List.of(
                jacopoSays.apply("I like football"),
                jacopoSays.apply("The weather is good today."),
                jacopoSays.apply("I love pizza"),
                jacopoSays.apply("The bear is terrific tv series!"),
                jacopoSays.apply("I have watched The Big Short tons of times!"),
                jacopoSays.apply("I read Harry Potter"),
                bobSays.apply("I read Wohpe"),
                bobSays.apply("I love basketball"),
                bobSays.apply("I do eat apples")

        );

        embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        store.removeAll();

        for (var sentence : randomSentences) {
            var content = embeddingModel.embed(sentence.text()).content();
            store.add(content, TextSegment.from(objectMapper.writeValueAsString(sentence), sentence.toMetadata()));
        }
    }

    @Test
    public void withRedis() throws IOException {
        assertEquals(9, redisClient.vcard("sentences"));

        Embedding queryEmbedding = embeddingModel.embed("What's the favourite food?")
                .content();
        EmbeddingSearchRequest query = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(10)
                .build();
        var result = store.search(query);

        assertEquals(9, result.matches().size());

        var first = result.matches().get(0);
        assertNotNull(first.embeddingId());

        var sentence = objectMapper.reader().readValue(first.embedded().text(), Sentence.class);
        assertEquals("I love pizza", sentence.text());
        assertEquals("jacopo", sentence.name());
        assertEquals(30, sentence.age());
    }

    @Test
    public void testFilters() throws IOException {
        Embedding queryEmbedding = embeddingModel.embed("What's the favourite food?")
                .content();

        Function<Filter, EmbeddingSearchRequest> queryWithFilter = filter -> EmbeddingSearchRequest
                .builder()
                .queryEmbedding(queryEmbedding)
                .filter(filter)
                .maxResults(10)
                .build();

        var result = store.search(queryWithFilter.apply(new IsEqualTo("name", "bob")));

        assertEquals(3, result.matches().size());

        var first = result.matches().get(0);
        assertNotNull(first.embeddingId());

        var sentence = objectMapper.reader().readValue(first.embedded().text(), Sentence.class);
        assertEquals("I do eat apples", sentence.text());
        assertEquals("bob", sentence.name());
        assertEquals(25, sentence.age());

        Function<Filter, Integer> searchAndCount = filter -> Optional.of(filter)
                .map(queryWithFilter)
                .map(store::search)
                .map(EmbeddingSearchResult::matches)
                .map(List::size)
                .orElse(-1);

        assertEquals(6, searchAndCount.apply(Filter.and(new IsEqualTo("name", "jacopo"), new IsLessThanOrEqualTo("age", 30))));
        assertEquals(3, searchAndCount.apply(Filter.not(Filter.and(new IsEqualTo("name", "jacopo"), new IsLessThanOrEqualTo("age", 30)))));
        assertEquals(9, searchAndCount.apply(Filter.or(Filter.and(new IsEqualTo("name", "jacopo"), new IsLessThanOrEqualTo("age", 30)), Filter.and(new IsEqualTo("name", "bob"), new IsEqualTo("age", 25)))));
        assertEquals(6, searchAndCount.apply(Filter.and(new IsIn("name", List.of("jacopo", "bob", "alice")), new IsNotIn("age", List.of(20, 25, 35)))));
    }

    @Test
    public void withMinScore() throws IOException {
        assertEquals(9, redisClient.vcard("sentences"));

        Embedding queryEmbedding = embeddingModel.embed("What's the favourite food?")
                .content();
        EmbeddingSearchRequest query = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(10)
                .minScore(0.6)
                .build();
        var result = store.search(query);

        assertEquals(4, result.matches().size());
    }


    @Override
    public void close() throws Exception {
        redisClient.close();
        redis.close();
    }
}
