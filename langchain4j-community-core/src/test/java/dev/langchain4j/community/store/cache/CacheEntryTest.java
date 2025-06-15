package dev.langchain4j.community.store.cache;

import dev.langchain4j.data.embedding.Embedding;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CacheEntryTest {

    private static final String TEXT = "hello";
    private static final Embedding EMBEDDING = Embedding.from(new float[]{1.0f, 2.0f});
    private static final Map<String, Object> METADATA = Map.of("key", "value");
    private static final String MODEL_NAME = "test-model";

    @Test
    void constructor_should_initialize_all_fields() {
        Instant now = Instant.now();
        CacheEntry entry = new CacheEntry(TEXT, EMBEDDING, METADATA, MODEL_NAME, now, now, 5);

        assertThat(entry.getText()).isEqualTo(TEXT);
        assertThat(entry.getEmbedding()).isEqualTo(EMBEDDING);
        assertThat(entry.getMetadata()).isEqualTo(METADATA);
        assertThat(entry.getModelName()).isEqualTo(MODEL_NAME);
        assertThat(entry.getInsertedAt()).isEqualTo(now);
        assertThat(entry.getAccessedAt()).isEqualTo(now);
        assertThat(entry.getAccessCount()).isEqualTo(5);
    }

    @Test
    void constructor_should_use_empty_metadata_when_null() {
        CacheEntry entry = new CacheEntry(TEXT, EMBEDDING, null, MODEL_NAME, null, null, 0);

        assertThat(entry.getMetadata()).isEmpty();
    }

    @Test
    void constructor_should_initialize_timestamps_when_null() {
        CacheEntry entry = new CacheEntry(TEXT, EMBEDDING, null, MODEL_NAME, null, null, 0);

        assertThat(entry.getInsertedAt()).isNotNull();
        assertThat(entry.getAccessedAt()).isEqualTo(entry.getInsertedAt());
    }

    @Test
    void simple_constructor_should_initialize_defaults() {
        CacheEntry entry = new CacheEntry(TEXT, EMBEDDING);

        assertThat(entry.getText()).isEqualTo(TEXT);
        assertThat(entry.getEmbedding()).isEqualTo(EMBEDDING);
        assertThat(entry.getMetadata()).isEmpty();
        assertThat(entry.getModelName()).isNull();
        assertThat(entry.getAccessCount()).isZero();
    }

    @Test
    void metadata_constructor_should_work() {
        CacheEntry entry = new CacheEntry(TEXT, EMBEDDING, METADATA);

        assertThat(entry.getMetadata()).isEqualTo(METADATA);
    }

    @Test
    void metadata_and_modelName_constructor_should_work() {
        CacheEntry entry = new CacheEntry(TEXT, EMBEDDING, METADATA, MODEL_NAME);

        assertThat(entry.getModelName()).isEqualTo(MODEL_NAME);
    }

    @Test
    void markAccessed_should_increment_accessCount_and_update_accessedAt() throws InterruptedException {
        CacheEntry original = new CacheEntry(TEXT, EMBEDDING);
        Thread.sleep(5); // ensure time difference
        CacheEntry updated = original.markAccessed();

        assertThat(updated.getAccessCount()).isEqualTo(original.getAccessCount() + 1);
        assertThat(updated.getAccessedAt()).isAfter(original.getAccessedAt());
        assertThat(updated.getInsertedAt()).isEqualTo(original.getInsertedAt());
    }

    @Test
    void withMetadata_should_merge_metadata() {
        CacheEntry original = new CacheEntry(TEXT, EMBEDDING, Map.of("a", "1"));
        Map<String, Object> more = Map.of("b", "2", "a", "override");

        CacheEntry merged = original.withMetadata(more);

        assertThat(merged.getMetadata()).containsEntry("a", "override").containsEntry("b", "2");
    }

    @Test
    void withMetadata_should_return_self_if_input_null_or_empty() {
        CacheEntry original = new CacheEntry(TEXT, EMBEDDING, Map.of("a", "1"));

        assertThat(original.withMetadata(null)).isSameAs(original);
        assertThat(original.withMetadata(Map.of())).isSameAs(original);
    }

    @Test
    void equals_should_work_correctly() {
        Instant now = Instant.now();
        CacheEntry entry1 = new CacheEntry(TEXT, EMBEDDING, METADATA, MODEL_NAME, now, now, 1);
        CacheEntry entry2 = new CacheEntry(TEXT, EMBEDDING, METADATA, MODEL_NAME, now, now, 1);
        CacheEntry entry3 = new CacheEntry("different", EMBEDDING, METADATA, MODEL_NAME, now, now, 1);

        assertThat(entry1).isEqualTo(entry2)
                .isNotEqualTo(entry3)
                .isNotEqualTo(null)
                .isNotEqualTo("not a CacheEntry");
    }

    @Test
    void hashCode_should_be_consistent_with_equals() {
        Instant now = Instant.now();
        CacheEntry entry1 = new CacheEntry(TEXT, EMBEDDING, METADATA, MODEL_NAME, now, now, 1);
        CacheEntry entry2 = new CacheEntry(TEXT, EMBEDDING, METADATA, MODEL_NAME, now, now, 1);

        assertThat(entry1).hasSameHashCodeAs(entry2);
    }

    @Test
    void toString_should_include_key_fields() {
        CacheEntry entry = new CacheEntry(TEXT, EMBEDDING, METADATA, MODEL_NAME);
        String toString = entry.toString();

        assertThat(toString).contains(TEXT)
                .contains(MODEL_NAME)
                .contains("metadata.size=" + METADATA.size());
    }

    @Test
    void metadata_should_be_unmodifiable() {
        CacheEntry entry = new CacheEntry(TEXT, EMBEDDING, METADATA);
        assertThatThrownBy(() -> entry.getMetadata().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
