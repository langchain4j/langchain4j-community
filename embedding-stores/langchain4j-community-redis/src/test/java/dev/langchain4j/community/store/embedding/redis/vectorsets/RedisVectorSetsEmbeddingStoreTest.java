package dev.langchain4j.community.store.embedding.redis.vectorsets;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.VAddParams;
import redis.clients.jedis.params.VSimParams;
import redis.clients.jedis.resps.VSimScoreAttribs;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisVectorSetsEmbeddingStoreTest {

    private final UnifiedJedis redis;
    private final RedisVectorSetsEmbeddingStore store;
    private final EmbeddingModel embeddingModel;

    final Function<String, Sentence> jacopoSays = text -> new Sentence("jacopo", text, 30);

    final Function<String, Sentence> bobSays = text -> new Sentence("bob", text, 25);

    public RedisVectorSetsEmbeddingStoreTest() {
        this.redis = mock(UnifiedJedis.class);

        this.store = new RedisVectorSetsEmbeddingStore(redis, "sentences");
        embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();
    }

    @Test
    void add() {
        var content = embeddingModel.embed(jacopoSays.apply("I love pizza").text()).content();
        store.add(content);
        verify(redis, times(1)).vadd(eq("sentences"), isA(float[].class), anyString(), any(VAddParams.class));

        store.add("id:1", content);
        verify(redis, times(1)).vadd(eq("sentences"), isA(float[].class), eq("id:1"), any(VAddParams.class));

        store.add(content, TextSegment.from("test"));
        verify(redis, times(3)).vadd(eq("sentences"), isA(float[].class), anyString(), any(VAddParams.class));
    }

    @Test
    void addAll() {
        var randomSentences = List.of(
                jacopoSays.apply("I like football"),
                jacopoSays.apply("The weather is good today."),
                jacopoSays.apply("I love pizza"),
                jacopoSays.apply("The bear is terrific tv series!"),
                jacopoSays.apply("I have watched The Big Short tons of times!"),
                jacopoSays.apply("I read Harry Potter"),
                bobSays.apply("I read Wohpe"),
                bobSays.apply("I love basketball")
        );

        var contents = randomSentences
                .stream()
                .map(Sentence::text)
                .map(TextSegment::from)
                .map(embeddingModel::embed)
                .map(Response::content)
                .toList();

        store.addAll(contents);

        verify(redis, times(randomSentences.size())).vadd(eq("sentences"), isA(float[].class), anyString(), any(VAddParams.class));
    }


    @Test
    void search() {
        var query = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed("What about pizza?").content())
                .maxResults(10)
                .minScore(0.4)
                .filter(new IsEqualTo("name", "jacopo"))
                .build();

        when(redis.vsimWithScoresAndAttribs(eq("sentences"), isA(float[].class), any(VSimParams.class)))
                .thenReturn(Map.of("id:1", new VSimScoreAttribs(0.9, "test")));

        when(redis.vemb(eq("sentences"), eq("id:1")))
                .thenReturn(List.of(0.9, 0.1, 0.5));

        var result = store.search(query);


        assertEquals(1, result.matches().size());
    }

    @Test
    void removeAll() {
        store.removeAll();
        verify(redis, times(1)).unlink("sentences");

        store.removeAll(List.of("id:1", "id:2", "id:3"));
        store.remove("id:3");

        verify(redis, times(4)).vrem(eq("sentences"), anyString());
    }

    @Test
    void remove() {
    }

    @Test
    void testAddAll1() {
    }
}
