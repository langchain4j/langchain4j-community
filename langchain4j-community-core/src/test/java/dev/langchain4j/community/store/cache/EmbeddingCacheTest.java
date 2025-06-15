package dev.langchain4j.community.store.cache;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmbeddingCacheTest {

    static class TestEmbeddingCache implements EmbeddingCache {

        private final Map<String, Embedding> store = new HashMap<>();

        @Override
        public Optional<Embedding> get(String text) {
            return Optional.ofNullable(store.get(text));
        }

        @Override
        public Map<String, Embedding> get(List<String> texts) {
            Map<String, Embedding> result = new HashMap<>();
            for (String text : texts) {
                if (store.containsKey(text)) {
                    result.put(text, store.get(text));
                }
            }
            return result;
        }

        @Override
        public Map<String, Boolean> exists(List<String> texts) {
            Map<String, Boolean> result = new HashMap<>();
            for (String text : texts) {
                result.put(text, store.containsKey(text));
            }
            return result;
        }

        @Override
        public void put(String text, Embedding embedding) {
            store.put(text, embedding);
        }

        @Override
        public void put(Map<String, Embedding> embeddings) {
            store.putAll(embeddings);
        }

        @Override
        public boolean remove(String text) {
            return store.remove(text) != null;
        }

        @Override
        public Map<String, Boolean> remove(List<String> texts) {
            Map<String, Boolean> result = new HashMap<>();
            for (String text : texts) {
                result.put(text, store.remove(text) != null);
            }
            return result;
        }

        @Override
        public void clear() {
            store.clear();
        }
    }

    private EmbeddingCache cache;

    @BeforeEach
    void setUp() {
        cache = new TestEmbeddingCache();
    }

    @Test
    void should_get_existing_embedding() {
        Embedding embedding = Embedding.from(new float[] {1f, 2f});
        cache.put("hello", embedding);

        Optional<Embedding> result = cache.get("hello");

        assertThat(result).isPresent().contains(embedding);
    }

    @Test
    void should_return_empty_optional_when_get_missing_embedding() {
        Optional<Embedding> result = cache.get("missing");

        assertThat(result).isEmpty();
    }

    @Test
    void should_get_with_metadata_from_default_method() {
        Embedding embedding = Embedding.from(new float[] {1f});
        cache.put("foo", embedding);

        Optional<Map.Entry<Embedding, Map<String, Object>>> result = cache.getWithMetadata("foo");

        assertThat(result).isPresent();
        assertThat(result.get().getKey()).isEqualTo(embedding);
        assertThat(result.get().getValue()).isEmpty();
    }

    @Test
    void should_get_multiple_embeddings() {
        Embedding a = Embedding.from(new float[] {1});
        Embedding b = Embedding.from(new float[] {2});
        cache.put("a", a);
        cache.put("b", b);

        Map<String, Embedding> result = cache.get(List.of("a", "b", "c"));

        assertThat(result).hasSize(2).containsEntry("a", a).containsEntry("b", b);
    }

    @Test
    void should_get_with_metadata_for_multiple_texts() {
        Embedding a = Embedding.from(new float[] {1});
        Embedding b = Embedding.from(new float[] {2});
        cache.put("a", a);
        cache.put("b", b);

        Map<String, Map.Entry<Embedding, Map<String, Object>>> result = cache.getWithMetadata(List.of("a", "b", "x"));

        assertThat(result).hasSize(2);
        assertThat(result.get("a").getKey()).isEqualTo(a);
        assertThat(result.get("a").getValue()).isEmpty();
        assertThat(result.get("b").getKey()).isEqualTo(b);
    }

    @Test
    void should_check_exists() {
        cache.put("yes", Embedding.from(new float[] {1}));

        Map<String, Boolean> result = cache.exists(List.of("yes", "no"));

        assertThat(result).containsEntry("yes", true).containsEntry("no", false);
    }

    @Test
    void should_support_put_with_metadata_default_method() {
        Embedding emb = Embedding.from(new float[] {1});
        cache.put("key", emb, Map.of("meta", 123));

        assertThat(cache.get("key")).contains(emb);
    }

    @Test
    void should_support_put_with_metadata_and_ttl_default_method() {
        Embedding emb = Embedding.from(new float[] {9});
        cache.put("k", emb, Map.of("meta", "value"), 999L);

        assertThat(cache.get("k")).contains(emb);
    }

    @Test
    void should_support_put_with_metadata_map() {
        Embedding e1 = Embedding.from(new float[] {1});
        Embedding e2 = Embedding.from(new float[] {2});

        Map<String, Map.Entry<Embedding, Map<String, Object>>> entries = Map.of(
                "x", new AbstractMap.SimpleEntry<>(e1, Map.of("foo", "bar")),
                "y", new AbstractMap.SimpleEntry<>(e2, Map.of()));

        cache.putWithMetadata(entries);

        assertThat(cache.get("x")).contains(e1);
        assertThat(cache.get("y")).contains(e2);
    }

    @Test
    void should_remove_single_entry() {
        cache.put("gone", Embedding.from(new float[] {1}));

        boolean removed = cache.remove("gone");

        assertThat(removed).isTrue();
        assertThat(cache.get("gone")).isEmpty();
    }

    @Test
    void should_remove_multiple_entries() {
        cache.put("a", Embedding.from(new float[] {1}));
        cache.put("b", Embedding.from(new float[] {2}));

        Map<String, Boolean> result = cache.remove(List.of("a", "b", "c"));

        assertThat(result).containsEntry("a", true).containsEntry("b", true).containsEntry("c", false);
    }

    @Test
    void should_clear_all_entries() {
        cache.put("1", Embedding.from(new float[] {1}));
        cache.put("2", Embedding.from(new float[] {2}));

        cache.clear();

        assertThat(cache.get("1")).isEmpty();
        assertThat(cache.get("2")).isEmpty();
    }

    @Test
    void should_return_empty_when_finding_by_metadata_by_default() {
        Map<String, Map.Entry<Embedding, Map<String, Object>>> result =
                cache.findByMetadata(Map.of("tag", "value"), 10);

        assertThat(result).isEmpty();
    }
}
