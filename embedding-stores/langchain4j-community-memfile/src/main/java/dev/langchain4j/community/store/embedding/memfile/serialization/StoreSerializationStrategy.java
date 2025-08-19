package dev.langchain4j.community.store.embedding.memfile.serialization;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.community.store.embedding.memfile.MemFileEmbeddingStore;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Strategy interface for serializing and deserializing {@link MemFileEmbeddingStore} instances.
 * <p>
 * This interface defines the contract for converting embedding stores to and from various serialized formats
 * (such as JSON, XML, binary, etc.) and provides methods for both string-based and file-based operations.
 * Implementations can choose different serialization formats and strategies while maintaining a consistent API.
 *
 *
 * <p>
 * <b>Design Pattern:</b> This follows the Strategy design pattern, allowing different serialization
 * approaches to be plugged in without changing the client code. The embedding store delegates all
 * serialization concerns to the strategy implementation.
 *
 *
 * <p>
 * <b>Serialization Scope:</b> Implementations should serialize the embedding store's metadata and structure,
 * including:
 * <ul>
 * <li>All embedding vectors and their associated IDs</li>
 * <li>References to embedded content files (chunk file paths)</li>
 * <li>Configuration settings (chunk storage directory, cache size)</li>
 * <li>Any other metadata necessary to fully restore the store's state</li>
 * </ul>
 *
 * <p>
 * <b>Important Note:</b> The actual embedded content (e.g., TextSegment objects) stored in separate
 * chunk files is typically NOT included in the serialized data. Only references to these files are
 * serialized. This design keeps the serialized data compact while requiring that the original chunk
 * files remain accessible for full functionality after deserialization.
 *
 *
 * <p>
 * <b>Thread Safety:</b> Implementations should be thread-safe for concurrent serialization operations,
 * but individual method calls may not be atomic. Callers should ensure appropriate synchronization
 * when serializing stores that are being modified concurrently.
 *
 *
 * <p>
 * <b>Error Handling:</b> All methods may throw {@link RuntimeException} or its subclasses to indicate
 * serialization/deserialization failures. Implementations should provide meaningful error messages
 * and preserve stack traces for debugging.
 *
 *
 * <p>
 * <b>Example usage:</b>
 *
 * <pre>{@code
 * // Choose a serialization strategy
 * StoreSerializationStrategy<TextSegment> strategy = new JsonStoreSerializationStrategy<>();
 *
 * // Create and populate an embedding store
 * MemFileEmbeddingStore<TextSegment> store = new MemFileEmbeddingStore<>();
 * store.add(embedding, textSegment);
 *
 * // Serialize to string
 * String serializedData = strategy.serialize(store);
 *
 * // Serialize to file
 * strategy.serializeToFile(store, Paths.get("backup.json"));
 *
 * // Deserialize from string
 * MemFileEmbeddingStore<TextSegment> restoredStore = strategy.deserialize(serializedData);
 *
 * // Deserialize from file
 * MemFileEmbeddingStore<TextSegment> loadedStore = strategy.deserializeFromFile("backup.json");
 * }</pre>
 *
 * @param <T> the type of embedded objects stored in the embedding store (typically {@link dev.langchain4j.data.segment.TextSegment})
 * @see JsonStoreSerializationStrategy
 * @see MemFileEmbeddingStore
 */
public interface StoreSerializationStrategy<T> {

    /**
     * Serializes the given embedding store to a string representation.
     * <p>
     * This method converts the complete state of the embedding store into a string format
     * that can be persisted, transmitted, or cached. The exact format depends on the
     * implementation (JSON, XML, etc.).
     *
     *
     * <p>
     * <b>Serialization Content:</b> The serialized string should include:
     * <ul>
     * <li>All embedding entries with their IDs and vector data</li>
     * <li>References to chunk files (not the actual content)</li>
     * <li>Store configuration (chunk directory, cache size)</li>
     * <li>Any metadata required for complete restoration</li>
     * </ul>
     * <p>
     *
     * @param store the embedding store to serialize; must not be {@code null}
     * @return a string representation of the embedding store that can be used with {@link #deserialize(String)}
     * @throws IllegalArgumentException if the store is {@code null}
     * @throws RuntimeException         if serialization fails due to I/O errors or format-specific issues
     * @see #deserialize(String)
     * @see #serializeToFile(MemFileEmbeddingStore, Path)
     */
    String serialize(MemFileEmbeddingStore<T> store);

    /**
     * Serializes the given embedding store directly to a file.
     * <p>
     * This method writes the serialized representation of the embedding store directly to
     * the specified file path. The file will be created if it doesn't exist, or overwritten
     * if it does exist. Parent directories will be created as needed.
     *
     *
     * <b>File Operations:</b> The implementation should handle:
     * <ul>
     * <li>Creating parent directories if they don't exist</li>
     * <li>Overwriting existing files atomically where possible</li>
     * <li>Proper cleanup in case of write failures</li>
     * </ul>
     *
     * @param store    the embedding store to serialize; must not be {@code null}
     * @param filePath the path where the serialized data should be written; must not be {@code null}
     * @throws IllegalArgumentException if either parameter is {@code null}
     * @throws RuntimeException         if the file cannot be created or written to, or if serialization fails
     * @see #deserializeFromFile(Path)
     * @see #serialize(MemFileEmbeddingStore)
     */
    void serializeToFile(MemFileEmbeddingStore<T> store, Path filePath);

    default void serializeToFile(MemFileEmbeddingStore<T> store, String filePath) {
        ensureNotNull(filePath, "filePath");
        serializeToFile(store, Paths.get(filePath));
    }

    /**
     * Deserializes an embedding store from its string representation.
     * <p>
     * This method reconstructs a {@link MemFileEmbeddingStore} from data that was previously
     * created by {@link #serialize(MemFileEmbeddingStore)}. The deserialized store will have
     * the same configuration and embedding entries as the original.
     *
     *
     * <p>
     * <b>Restoration Process:</b> Deserialization typically involves:
     * <ul>
     * <li>Parsing the serialized format to extract metadata</li>
     * <li>Recreating the store with original configuration</li>
     * <li>Restoring all embedding entries and their references</li>
     * <li>Setting up internal structures (cache, etc.) but not preloading content</li>
     * </ul>
     *
     *
     * <p>
     * <b>Dependencies:</b> The deserialized store requires:
     * <ul>
     * <li>Access to the original chunk storage directory</li>
     * <li>All referenced chunk files must exist and be readable</li>
     * <li>Proper file permissions for the chunk directory and files</li>
     * </ul>
     *
     * @param data the serialized string representation of an embedding store; must not be {@code null} or blank
     * @return a new {@link MemFileEmbeddingStore} instance restored from the serialized data
     * @throws IllegalArgumentException if the data is {@code null}, blank, or has an invalid format
     * @throws RuntimeException         if deserialization fails due to parsing errors or I/O issues
     * @see #serialize(MemFileEmbeddingStore)
     * @see #deserializeFromFile(Path)
     */
    MemFileEmbeddingStore<T> deserialize(String data);

    /**
     * Deserializes an embedding store from a file.
     * <p>
     * This method reads the serialized data from the specified file and reconstructs the
     * embedding store. The file must contain data that was previously created by
     * {@link #serializeToFile(MemFileEmbeddingStore, Path)} or compatible serialization method.
     *
     * <p>
     * <b>File Requirements:</b> The file must:
     * <ul>
     * <li>Exist and be readable</li>
     * <li>Contain valid serialized store data</li>
     * <li>Be in the format expected by this strategy implementation</li>
     * <li>Not be corrupted or partially written</li>
     * </ul>
     *
     * @param filePath the path to the file containing serialized store data; must not be {@code null}
     * @return a new {@link MemFileEmbeddingStore} instance restored from the file data
     * @throws IllegalArgumentException if the file path is {@code null}
     * @throws RuntimeException         if the file doesn't exist, cannot be read, or contains invalid data
     * @see #serializeToFile(MemFileEmbeddingStore, Path)
     * @see #deserialize(String)
     */
    MemFileEmbeddingStore<T> deserializeFromFile(Path filePath);

    default MemFileEmbeddingStore<T> deserializeFromFile(String filePath) {
        ensureNotNull(filePath, "filePath");
        return deserializeFromFile(Paths.get(filePath));
    }
}
