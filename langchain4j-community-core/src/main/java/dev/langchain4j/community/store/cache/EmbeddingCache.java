package dev.langchain4j.community.store.cache;

import dev.langchain4j.data.embedding.Embedding;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interface for a cache that stores embeddings to avoid recomputation.
 *
 * <p>This cache maps text to its corresponding embedding, allowing applications
 * to reuse previously computed embeddings instead of regenerating them.</p>
 */
public interface EmbeddingCache {

    /**
     * Retrieves an embedding for the given text if it exists in the cache.
     *
     * @param text The text whose embedding should be retrieved
     * @return An Optional containing the embedding if found, or empty if not in cache
     */
    Optional<Embedding> get(String text);

    /**
     * Retrieves an embedding along with its metadata for the given text if it exists in the cache.
     *
     * @param text The text whose embedding should be retrieved
     * @return An Optional containing a map entry with the embedding as key and metadata as value,
     * or empty if not in cache
     */
    default Optional<Map.Entry<Embedding, Map<String, Object>>> getWithMetadata(String text) {
        return get(text).map(embedding -> new AbstractMap.SimpleEntry<>(embedding, Map.of()));
    }

    /**
     * Retrieves multiple embeddings for the given texts if they exist in the cache.
     * The returned map contains embeddings for texts that were found in the cache.
     * Texts not found in the cache will not be included in the map.
     *
     * @param texts The list of texts whose embeddings should be retrieved
     * @return A map of text to embedding for all texts found in the cache
     */
    Map<String, Embedding> get(List<String> texts);

    /**
     * Retrieves multiple embeddings with their metadata for the given texts if they exist in the cache.
     * The returned map contains entries for texts that were found in the cache.
     * Texts not found in the cache will not be included in the map.
     *
     * @param texts The list of texts whose embeddings should be retrieved
     * @return A map of text to embedding/metadata entry for all texts found in the cache
     */
    default Map<String, Map.Entry<Embedding, Map<String, Object>>> getWithMetadata(List<String> texts) {
        // The default implementation falls back to get without metadata
        Map<String, Embedding> embeddings = get(texts);
        Map<String, Map.Entry<Embedding, Map<String, Object>>> result = new java.util.HashMap<>();

        for (Map.Entry<String, Embedding> entry : embeddings.entrySet()) {
            Map<String, Object> emptyMetadata = Map.of();
            result.put(entry.getKey(), new AbstractMap.SimpleEntry<>(entry.getValue(), emptyMetadata));
        }

        return result;
    }

    /**
     * Checks if embeddings for the given texts exist in the cache.
     * The returned map indicates whether each text has an embedding in the cache.
     *
     * @param texts The list of texts to check
     * @return A map of text to boolean indicating whether each text has an embedding in the cache
     */
    Map<String, Boolean> exists(List<String> texts);

    /**
     * Stores an embedding for the given text in the cache.
     *
     * @param text      The text to associate with the embedding
     * @param embedding The embedding to store
     */
    void put(String text, Embedding embedding);

    /**
     * Stores an embedding with metadata for the given text in the cache.
     *
     * @param text      The text to associate with the embedding
     * @param embedding The embedding to store
     * @param metadata  Optional metadata to store with the embedding
     */
    default void put(String text, Embedding embedding, Map<String, Object> metadata) {
        // Default implementation ignores metadata
        put(text, embedding);
    }

    /**
     * Stores an embedding with metadata and a custom TTL for the given text in the cache.
     *
     * @param text       The text to associate with the embedding
     * @param embedding  The embedding to store
     * @param metadata   Optional metadata to store with the embedding
     * @param ttlSeconds Time-to-live in seconds for this specific entry (0 for default TTL)
     */
    default void put(String text, Embedding embedding, Map<String, Object> metadata, long ttlSeconds) {
        // Default implementation ignores metadata and custom TTL
        put(text, embedding);
    }

    /**
     * Stores multiple embeddings in the cache.
     *
     * @param embeddings A map of text to embedding to store in the cache
     */
    void put(Map<String, Embedding> embeddings);

    /**
     * Stores multiple embeddings with metadata in the cache.
     *
     * @param embeddings A map of text to embedding/metadata entry to store in the cache
     */
    default void putWithMetadata(Map<String, Map.Entry<Embedding, Map<String, Object>>> embeddings) {
        // Default implementation extracts embeddings and ignores metadata
        Map<String, Embedding> embeddingsOnly = new java.util.HashMap<>();
        for (Map.Entry<String, Map.Entry<Embedding, Map<String, Object>>> entry : embeddings.entrySet()) {
            embeddingsOnly.put(entry.getKey(), entry.getValue().getKey());
        }
        put(embeddingsOnly);
    }

    /**
     * Removes the embedding for the given text from the cache if it exists.
     *
     * @param text The text whose embedding should be removed
     * @return true if an embedding was removed, false if no embedding existed for the text
     */
    boolean remove(String text);

    /**
     * Removes multiple embeddings from the cache.
     * Returns a map indicating whether each text's embedding was removed.
     *
     * @param texts The list of texts whose embeddings should be removed
     * @return A map of text to boolean indicating whether each text's embedding was removed
     */
    Map<String, Boolean> remove(List<String> texts);

    /**
     * Clears all entries from the cache.
     */
    void clear();

    /**
     * Searches for embeddings in the cache that match the given metadata filter.
     * The filter is a map of field names to values that must be matched.
     *
     * @param filter The metadata filter to apply
     * @param limit  Maximum number of results to return (0 for unlimited)
     * @return A map of text to embedding/metadata entry for all matching entries
     */
    default Map<String, Map.Entry<Embedding, Map<String, Object>>> findByMetadata(
            Map<String, Object> filter, int limit) {
        // Default implementation returns empty map (no filtering support)
        return Map.of();
    }
}
