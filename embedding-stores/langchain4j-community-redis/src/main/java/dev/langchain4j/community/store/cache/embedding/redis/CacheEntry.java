package dev.langchain4j.community.store.cache.embedding.redis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import dev.langchain4j.data.embedding.Embedding;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Represents a cache entry for an embedding, including metadata.
 * This class is used for serialization/deserialization of entries in the Redis cache.
 */
public class CacheEntry {

    private final String text;

    @JsonSerialize(using = EmbeddingSerializer.class)
    @JsonDeserialize(using = EmbeddingDeserializer.class)
    private final Embedding embedding;

    private final Map<String, Object> metadata;
    private final String modelName;
    private final Instant insertedAt;
    private final Instant accessedAt;
    private final long accessCount;

    /**
     * Creates a new cache entry.
     *
     * @param text The text that was embedded
     * @param embedding The embedding vector
     * @param metadata Optional metadata associated with this embedding
     * @param modelName Optional name of the model that created this embedding
     * @param insertedAt When this entry was first inserted into the cache
     * @param accessedAt When this entry was last accessed
     * @param accessCount How many times this entry has been accessed
     */
    @JsonCreator
    public CacheEntry(
            @JsonProperty("text") String text,
            @JsonProperty("embedding") Embedding embedding,
            @JsonProperty("metadata") Map<String, Object> metadata,
            @JsonProperty("modelName") String modelName,
            @JsonProperty("insertedAt") Instant insertedAt,
            @JsonProperty("accessedAt") Instant accessedAt,
            @JsonProperty("accessCount") long accessCount) {
        this.text = text;
        this.embedding = embedding;
        this.metadata = metadata == null ? Collections.emptyMap() : Collections.unmodifiableMap(metadata);
        this.modelName = modelName;
        this.insertedAt = insertedAt == null ? Instant.now() : insertedAt;
        this.accessedAt = accessedAt == null ? insertedAt : accessedAt;
        this.accessCount = accessCount;
    }

    /**
     * Creates a new cache entry with default settings.
     *
     * @param text The text that was embedded
     * @param embedding The embedding vector
     */
    public CacheEntry(String text, Embedding embedding) {
        this(text, embedding, Collections.emptyMap(), null, Instant.now(), Instant.now(), 0);
    }

    /**
     * Creates a new cache entry with metadata.
     *
     * @param text The text that was embedded
     * @param embedding The embedding vector
     * @param metadata Optional metadata associated with this embedding
     */
    public CacheEntry(String text, Embedding embedding, Map<String, Object> metadata) {
        this(text, embedding, metadata, null, Instant.now(), Instant.now(), 0);
    }

    /**
     * Creates a new cache entry with metadata and model name.
     *
     * @param text The text that was embedded
     * @param embedding The embedding vector
     * @param metadata Optional metadata associated with this embedding
     * @param modelName Optional name of the model that created this embedding
     */
    public CacheEntry(String text, Embedding embedding, Map<String, Object> metadata, String modelName) {
        this(text, embedding, metadata, modelName, Instant.now(), Instant.now(), 0);
    }

    /**
     * Creates a new cache entry instance with an updated access timestamp and count.
     *
     * @return A new CacheEntry with updated access statistics
     */
    public CacheEntry markAccessed() {
        return new CacheEntry(
                this.text,
                this.embedding,
                this.metadata,
                this.modelName,
                this.insertedAt,
                Instant.now(),
                this.accessCount + 1);
    }

    /**
     * Creates a new cache entry with the given metadata merged with existing metadata.
     *
     * @param additionalMetadata The metadata to add or update
     * @return A new CacheEntry with the merged metadata
     */
    public CacheEntry withMetadata(Map<String, Object> additionalMetadata) {
        if (additionalMetadata == null || additionalMetadata.isEmpty()) {
            return this;
        }

        Map<String, Object> newMetadata = new java.util.HashMap<>(this.metadata);
        newMetadata.putAll(additionalMetadata);

        return new CacheEntry(
                this.text,
                this.embedding,
                newMetadata,
                this.modelName,
                this.insertedAt,
                this.accessedAt,
                this.accessCount);
    }

    /**
     * Gets the text that was embedded.
     *
     * @return The text
     */
    public String getText() {
        return text;
    }

    /**
     * Gets the embedding vector.
     *
     * @return The embedding
     */
    public Embedding getEmbedding() {
        return embedding;
    }

    /**
     * Gets the metadata associated with this embedding.
     *
     * @return The metadata
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Gets the name of the model that created this embedding.
     *
     * @return The model name
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * Gets when this entry was first inserted into the cache.
     *
     * @return The insertion timestamp
     */
    public Instant getInsertedAt() {
        return insertedAt;
    }

    /**
     * Gets when this entry was last accessed.
     *
     * @return The last access timestamp
     */
    public Instant getAccessedAt() {
        return accessedAt;
    }

    /**
     * Gets how many times this entry has been accessed.
     *
     * @return The access count
     */
    public long getAccessCount() {
        return accessCount;
    }

    @Override
    public String toString() {
        return "CacheEntry{" + "text='"
                + text + '\'' + ", modelName='"
                + modelName + '\'' + ", insertedAt="
                + insertedAt + ", accessedAt="
                + accessedAt + ", accessCount="
                + accessCount + ", metadata.size="
                + metadata.size() + '}';
    }
}
