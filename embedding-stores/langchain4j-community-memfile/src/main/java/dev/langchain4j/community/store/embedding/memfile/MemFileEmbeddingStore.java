package dev.langchain4j.community.store.embedding.memfile;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.util.Comparator.comparingDouble;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.community.store.embedding.memfile.serialization.StoreSerializationStrategy;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.CosineSimilarity;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.RelevanceScore;
import dev.langchain4j.store.embedding.filter.Filter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link EmbeddingStore} implementation that stores embeddings in memory
 * while persisting associated embedded content (e.g.,
 * {@link dev.langchain4j.data.segment.TextSegment}) as JSON files in a local
 * directory.
 *
 * <p>
 * <b>Design highlights:</b>
 *
 * <ul>
 * <li>Embeddings are kept fully in memory for fast similarity search.</li>
 * <li>Embedded content is serialized to JSON and stored as separate files on
 * disk, reducing memory footprint for large content.</li>
 * <li>Optional LRU cache keeps frequently accessed embedded content in
 * memory.</li>
 * <li>Supports adding, removing, and searching embeddings with optional
 * metadata filtering.</li>
 * </ul>
 *
 * <p>
 * <b>Persistence of embedded content:</b>
 *
 * <ul>
 * <li>When an embedding is added with associated content, the content is saved
 * to the configured {@code chunkStorageDirectory}.</li>
 * <li>Content is reloaded from disk on demand and optionally cached for
 * reuse.</li>
 * <li>Chunk files are named after the embedding ID with a {@code .json}
 * extension.</li>
 * </ul>
 *
 * <p>
 * <b>Thread safety:</b>
 *
 * <ul>
 * <li>The store uses concurrent collections and is safe for concurrent
 * reads/writes.</li>
 * </ul>
 *
 * @param <Embedded> The type of the embedded object associated with an
 *                   embedding. Commonly
 *                   {@link dev.langchain4j.data.segment.TextSegment}.
 */
public class MemFileEmbeddingStore<Embedded> implements EmbeddingStore<Embedded> {

    private static final Logger log = LoggerFactory.getLogger(MemFileEmbeddingStore.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private final ConcurrentLinkedQueue<Entry<Embedded>> entries;
    private final Path chunkStorageDirectory;
    private final Map<String, Embedded> chunkCache;
    private final int cacheSize;

    /**
     * Creates a new MemFileEmbeddingStore with default settings. Uses a temporary
     * directory for chunk storage and no caching.
     */
    public MemFileEmbeddingStore() {
        this(createDefaultChunkDirectory(), 0);
    }

    /**
     * Creates a new MemFileEmbeddingStore with specified chunk storage directory.
     *
     * @param chunkStorageDirectory Directory where embedded content will be stored
     *                              as files
     */
    public MemFileEmbeddingStore(Path chunkStorageDirectory) {
        this(chunkStorageDirectory, 0);
    }

    /**
     * Creates a new MemFileEmbeddingStore with specified chunk storage directory
     * and cache size.
     *
     * @param chunkStorageDirectory Directory where embedded content will be stored
     *                              as files
     * @param cacheSize             Size of LRU cache for recently loaded chunks (0
     *                              = no caching)
     */
    public MemFileEmbeddingStore(Path chunkStorageDirectory, int cacheSize) {
        this.entries = new ConcurrentLinkedQueue<>();
        this.chunkStorageDirectory = ensureNotNull(chunkStorageDirectory, "chunkStorageDirectory");
        this.cacheSize = Math.max(0, cacheSize);
        this.chunkCache = cacheSize > 0 ? createLRUCache(cacheSize) : new ConcurrentHashMap<>();
        createChunkStorageDirectory();
        log.debug(
                "Created MemFileEmbeddingStore with storage directory: {} and cache size: {}",
                chunkStorageDirectory,
                cacheSize);
    }

    public MemFileEmbeddingStore(Collection<Entry<Embedded>> entries, Path chunkStorageDirectory, int cacheSize) {
        this.entries = new ConcurrentLinkedQueue<>(entries);
        this.chunkStorageDirectory = ensureNotNull(chunkStorageDirectory, "chunkStorageDirectory");
        this.cacheSize = Math.max(0, cacheSize);
        this.chunkCache = cacheSize > 0 ? createLRUCache(cacheSize) : new ConcurrentHashMap<>();
        createChunkStorageDirectory();
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        add(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, Embedded embedded) {
        String id = randomUUID();
        add(id, embedding, embedded);
        return id;
    }

    public void add(String id, Embedding embedding, Embedded embedded) {
        ensureNotBlank(id, "id");
        ensureNotNull(embedding, "embedding");

        String chunkFilePath = null;
        if (embedded != null) {
            chunkFilePath = saveChunkToFile(id, embedded);
        }

        entries.add(new Entry<>(id, embedding, chunkFilePath));
        log.debug("Added embedding with id: {} and chunk file: {}", id, chunkFilePath);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<Entry<Embedded>> newEntries = embeddings.stream()
                .map(embedding -> new Entry<Embedded>(randomUUID(), embedding, null))
                .collect(ArrayList::new, (list, entry) -> list.add(entry), ArrayList::addAll);

        return add(newEntries);
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<Embedded> embedded) {
        if (ids.size() != embeddings.size() || embeddings.size() != embedded.size()) {
            throw new IllegalArgumentException("The list of ids and embeddings and embedded must have the same size");
        }

        List<Entry<Embedded>> newEntries = new ArrayList<>(ids.size());

        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            Embedding embedding = embeddings.get(i);
            Embedded embeddedContent = embedded.get(i);

            String chunkFilePath = null;
            if (embeddedContent != null) {
                chunkFilePath = saveChunkToFile(id, embeddedContent);
            }

            newEntries.add(new Entry<>(id, embedding, chunkFilePath));
        }
        add(newEntries);
    }

    private List<String> add(List<Entry<Embedded>> newEntries) {
        entries.addAll(newEntries);

        return newEntries.stream().map(entry -> entry.id).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");

        entries.removeIf(entry -> {
            if (ids.contains(entry.id)) {
                deleteChunkFile(entry.chunkFilePath);
                chunkCache.remove(entry.id);
                return true;
            }
            return false;
        });
        log.debug("Removed {} embeddings", ids.size());
    }

    @Override
    public void removeAll(Filter filter) {
        ensureNotNull(filter, "filter");

        entries.removeIf(entry -> {
            if (entry.chunkFilePath != null) {
                Embedded embedded = loadChunkFromFile(entry.chunkFilePath);
                if (embedded instanceof TextSegment) {
                    boolean matches = filter.test(((TextSegment) embedded).metadata());
                    if (matches) {
                        deleteChunkFile(entry.chunkFilePath);
                        chunkCache.remove(entry.id);
                    }
                    return matches;
                }
            }
            return false;
        });
        log.debug("Removed embeddings matching filter");
    }

    @Override
    public void removeAll() {
        // Delete all chunk files
        for (Entry<Embedded> entry : entries) {
            deleteChunkFile(entry.chunkFilePath);
        }
        entries.clear();
        chunkCache.clear();
        log.debug("Removed all embeddings and chunk files");
    }

    @Override
    public EmbeddingSearchResult<Embedded> search(EmbeddingSearchRequest embeddingSearchRequest) {
        Comparator<EmbeddingMatch<Embedded>> comparator = comparingDouble(EmbeddingMatch::score);
        PriorityQueue<EmbeddingMatch<Embedded>> matches = new PriorityQueue<>(comparator);

        Filter filter = embeddingSearchRequest.filter();

        for (Entry<Embedded> entry : entries) {
            Embedded embedded = null;

            // Load embedded content from file if needed for filtering
            if (filter != null && entry.chunkFilePath != null) {
                embedded = loadChunkFromFile(entry.chunkFilePath);
                if (embedded instanceof TextSegment) {
                    Metadata metadata = ((TextSegment) embedded).metadata();
                    if (!filter.test(metadata)) {
                        continue;
                    }
                }
            }

            double cosineSimilarity =
                    CosineSimilarity.between(entry.embedding, embeddingSearchRequest.queryEmbedding());
            double score = RelevanceScore.fromCosineSimilarity(cosineSimilarity);

            if (score >= embeddingSearchRequest.minScore()) {
                // Load embedded content if not already loaded
                if (embedded == null && entry.chunkFilePath != null) {
                    embedded = loadChunkFromFile(entry.chunkFilePath);
                }

                matches.add(new EmbeddingMatch<>(score, entry.id, entry.embedding, embedded));
                if (matches.size() > embeddingSearchRequest.maxResults()) {
                    matches.poll();
                }
            }
        }

        List<EmbeddingMatch<Embedded>> result = new ArrayList<>(matches);
        result.sort(comparator);
        Collections.reverse(result);

        log.debug("Found {} matches for search request", result.size());
        return new EmbeddingSearchResult<>(result);
    }

    /**
     * Configures the chunk storage base directory.
     *
     * @param chunkStorageDirectory New directory for storing chunk files
     * @return A new MemFileEmbeddingStore instance with the specified directory
     */
    public MemFileEmbeddingStore<Embedded> withChunkStorageDirectory(Path chunkStorageDirectory) {
        return new MemFileEmbeddingStore<>(entries, chunkStorageDirectory, cacheSize);
    }

    public MemFileStoreData<Embedded> memFileStoreData() {
        return new MemFileStoreData<>(entries, chunkStorageDirectory.toString(), cacheSize);
    }

    /**
     * Serializes the entire embedding store to a string representation using the specified serialization strategy.
     * <p>
     * This method captures the complete state of the embedding store, including:
     * <ul>
     * <li>All embeddings and their associated IDs</li>
     * <li>References to embedded content files (chunk file paths)</li>
     * <li>Chunk storage directory configuration</li>
     * <li>Cache size configuration</li>
     * </ul>
     *
     *
     * <p>
     * <b>Note:</b> The actual embedded content (e.g., TextSegment objects) stored in chunk files
     * is not included in the serialized string. Only references to these files are serialized.
     * To fully restore the store, both the serialized string and the original chunk files
     * from the storage directory are required.
     *
     *
     * <p>
     * <b>Thread Safety:</b> This method is thread-safe and can be called concurrently
     * with other operations on the store.
     *
     *
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * MemFileEmbeddingStore<TextSegment> store = new MemFileEmbeddingStore<>();
     * JsonStoreSerializationStrategy<TextSegment> strategy = new JsonStoreSerializationStrategy<>();
     *
     * // Add some embeddings
     * store.add(embedding, textSegment);
     *
     * // Serialize to string
     * String serializedStore = store.serialize(strategy);
     * }</pre>
     *
     * @param storeSerializationStrategy the strategy to use for serialization; must not be {@code null}
     * @return a string representation of the embedding store that can be used for persistence or transfer
     * @throws RuntimeException if serialization fails due to I/O errors or strategy-specific issues
     * @see #deserialize(StoreSerializationStrategy, String)
     * @see #serializeToFile(StoreSerializationStrategy, Path)
     */
    public String serialize(StoreSerializationStrategy<Embedded> storeSerializationStrategy) {
        return storeSerializationStrategy.serialize(this);
    }

    /**
     * Serializes the entire embedding store to a file using the specified serialization strategy and file path.
     * <p>
     * This method is equivalent to calling {@link #serialize(StoreSerializationStrategy)} and then
     * writing the result to the specified file. The file will be created if it doesn't exist,
     * or overwritten if it does exist.
     *
     *
     * <p>
     * <b>File Structure:</b> The serialized data contains metadata about the embedding store
     * but not the actual embedded content. The chunk files containing the embedded content
     * remain in the original chunk storage directory and must be preserved separately.
     *
     *
     * <p>
     * <b>Backup Strategy:</b> For complete backup, you should:
     * <ol>
     * <li>Serialize the store metadata using this method</li>
     * <li>Backup the entire chunk storage directory</li>
     * </ol>
     *
     *
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * MemFileEmbeddingStore<TextSegment> store = new MemFileEmbeddingStore<>();
     * JsonStoreSerializationStrategy<TextSegment> strategy = new JsonStoreSerializationStrategy<>();
     * Path backupFile = Paths.get("/backup/store-metadata.json");
     *
     * // Serialize store metadata to file
     * store.serializeToFile(strategy, backupFile);
     * }</pre>
     *
     * @param storeSerializationStrategy the strategy to use for serialization; must not be {@code null}
     * @param filePath the path where the serialized data should be written; must not be {@code null}
     * @throws RuntimeException if the file cannot be created, written to, or if serialization fails
     * @see #deserializeFromFile(StoreSerializationStrategy, Path)
     * @see #serialize(StoreSerializationStrategy)
     */
    public void serializeToFile(StoreSerializationStrategy<Embedded> storeSerializationStrategy, Path filePath) {
        storeSerializationStrategy.serializeToFile(this, filePath);
    }

    /**
     * Serializes the entire embedding store to a file using the specified serialization strategy and file path string.
     * <p>
     * This is a convenience method that converts the string path to a {@link Path} object and delegates
     * to {@link #serializeToFile(StoreSerializationStrategy, Path)}.
     *
     *
     * <p>
     * <b>Path Resolution:</b> The file path can be absolute or relative. Relative paths are resolved
     * against the current working directory. The parent directories will be created if they don't exist
     * (depending on the serialization strategy implementation).
     *
     *
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * MemFileEmbeddingStore<TextSegment> store = new MemFileEmbeddingStore<>();
     * JsonStoreSerializationStrategy<TextSegment> strategy = new JsonStoreSerializationStrategy<>();
     *
     * // Serialize to relative path
     * store.serializeToFile(strategy, "backup/store.json");
     *
     * // Serialize to absolute path
     * store.serializeToFile(strategy, "/home/user/backups/store.json");
     * }</pre>
     *
     * @param storeSerializationStrategy the strategy to use for serialization; must not be {@code null}
     * @param filePath the file path as a string where the serialized data should be written; must not be {@code null} or blank
     * @throws RuntimeException if the file cannot be created, written to, or if serialization fails
     * @throws IllegalArgumentException if the file path is invalid
     * @see #deserializeFromFile(StoreSerializationStrategy, String)
     * @see #serializeToFile(StoreSerializationStrategy, Path)
     */
    public void serializeToFile(StoreSerializationStrategy<Embedded> storeSerializationStrategy, String filePath) {
        storeSerializationStrategy.serializeToFile(this, filePath);
    }

    /**
     * Deserializes an embedding store from its string representation using the specified serialization strategy.
     * <p>
     * This method creates a new {@code MemFileEmbeddingStore} instance from serialized data.
     * The deserialized store will have the same configuration (chunk storage directory, cache size)
     * and embedding entries as the original store that was serialized.
     *
     *
     * <p>
     * <b>Important:</b> This method only restores the store metadata and embedding vectors.
     * The actual embedded content (e.g., TextSegment objects) will be loaded on-demand from
     * the chunk files in the original chunk storage directory. Therefore, the chunk storage
     * directory and its files must be accessible and unchanged for the deserialized store
     * to function properly.
     *
     *
     * <p>
     * <b>Restoration Process:</b>
     * <ol>
     * <li>Parse the serialized string to extract store metadata</li>
     * <li>Create a new store instance with the original configuration</li>
     * <li>Restore all embedding entries with their IDs and chunk file references</li>
     * <li>Initialize the cache (if configured) but don't preload chunk content</li>
     * </ol>
     *
     *
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * JsonStoreSerializationStrategy<TextSegment> strategy = new JsonStoreSerializationStrategy<>();
     * String serializedData = "..."; // Previously serialized store data
     *
     * // Restore the store from serialized data
     * MemFileEmbeddingStore<TextSegment> restoredStore = store.deserialize(strategy, serializedData);
     *
     * // The restored store can be used normally
     * List<EmbeddingMatch<TextSegment>> results = restoredStore.search(searchRequest);
     * }</pre>
     *
     * @param storeSerializationStrategy the strategy to use for deserialization; must not be {@code null}
     * @param data the serialized string representation of the embedding store; must not be {@code null} or blank
     * @return a new {@code MemFileEmbeddingStore} instance restored from the serialized data
     * @throws RuntimeException if deserialization fails due to invalid data format, I/O errors, or strategy-specific issues
     * @throws IllegalArgumentException if the serialized data is malformed or incompatible
     * @see #serialize(StoreSerializationStrategy)
     * @see #deserializeFromFile(StoreSerializationStrategy, Path)
     */
    public MemFileEmbeddingStore<Embedded> deserialize(
            StoreSerializationStrategy<Embedded> storeSerializationStrategy, String data) {
        return storeSerializationStrategy.deserialize(data);
    }

    /**
     * Deserializes an embedding store from a file using the specified serialization strategy and file path.
     * <p>
     * This method reads the serialized data from the specified file and creates a new
     * {@code MemFileEmbeddingStore} instance. It is equivalent to reading the file content
     * and calling {@link #deserialize(StoreSerializationStrategy, String)}.
     *
     *
     * <p>
     * <b>File Requirements:</b> The file must contain data that was previously created by
     * {@link #serializeToFile(StoreSerializationStrategy, Path)} using a compatible serialization strategy.
     * The file must be readable and contain valid serialized store data.
     *
     *
     * <p>
     * <b>Directory Structure:</b> After deserialization, the restored store will reference
     * the same chunk storage directory as specified in the serialized data. Ensure that:
     * <ul>
     * <li>The chunk storage directory exists and is accessible</li>
     * <li>All referenced chunk files are present and unchanged</li>
     * <li>The application has read permissions for the chunk directory and files</li>
     * </ul>
     *
     *
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * JsonStoreSerializationStrategy<TextSegment> strategy = new JsonStoreSerializationStrategy<>();
     * Path backupFile = Paths.get("/backup/store-metadata.json");
     *
     * // Restore the store from file
     * MemFileEmbeddingStore<TextSegment> restoredStore = store.deserializeFromFile(strategy, backupFile);
     *
     * // Verify the restoration was successful
     * EmbeddingSearchResult<TextSegment> results = restoredStore.search(searchRequest);
     * System.out.println("Restored " + results.matches().size() + " embeddings");
     * }</pre>
     *
     * @param storeSerializationStrategy the strategy to use for deserialization; must not be {@code null}
     * @param filePath the path to the file containing serialized store data; must not be {@code null}
     * @return a new {@code MemFileEmbeddingStore} instance restored from the file data
     * @throws RuntimeException if the file cannot be read, doesn't exist, or if deserialization fails
     * @throws IllegalArgumentException if the file contains malformed or incompatible data
     * @see #serializeToFile(StoreSerializationStrategy, Path)
     * @see #deserialize(StoreSerializationStrategy, String)
     */
    public MemFileEmbeddingStore<Embedded> deserializeFromFile(
            StoreSerializationStrategy<Embedded> storeSerializationStrategy, Path filePath) {
        return storeSerializationStrategy.deserializeFromFile(filePath);
    }

    /**
     * Deserializes an embedding store from a file using the specified serialization strategy and file path string.
     * <p>
     * This is a convenience method that converts the string path to a {@link Path} object and delegates
     * to {@link #deserializeFromFile(StoreSerializationStrategy, Path)}.
     *
     *
     * <p>
     * <b>Path Resolution:</b> The file path can be absolute or relative. Relative paths are resolved
     * against the current working directory. The file must exist and be readable.
     *
     *
     * <p>
     * <b>Use Cases:</b> This method is particularly useful when:
     * <ul>
     * <li>Loading stores from configuration files that specify string paths</li>
     * <li>Integrating with systems that work with string-based file paths</li>
     * <li>Building command-line tools that accept file paths as arguments</li>
     * </ul>
     *
     *
     * <p>
     * <b>Example usage:</b>
     *
     * <pre>{@code
     * JsonStoreSerializationStrategy<TextSegment> strategy = new JsonStoreSerializationStrategy<>();
     *
     * // Load from relative path
     * MemFileEmbeddingStore<TextSegment> store1 = store.deserializeFromFile(strategy, "backup/store.json");
     *
     * // Load from absolute path
     * MemFileEmbeddingStore<TextSegment> store2 = store.deserializeFromFile(strategy, "/home/user/backups/store.json");
     *
     * // Load from user home directory
     * String userHome = System.getProperty("user.home");
     * MemFileEmbeddingStore<TextSegment> store3 = store.deserializeFromFile(strategy, userHome + "/store.json");
     * }</pre>
     *
     * @param storeSerializationStrategy the strategy to use for deserialization; must not be {@code null}
     * @param filePath the file path as a string containing serialized store data; must not be {@code null} or blank
     * @return a new {@code MemFileEmbeddingStore} instance restored from the file data
     * @throws RuntimeException if the file cannot be read, doesn't exist, or if deserialization fails
     * @throws IllegalArgumentException if the file path is invalid or contains malformed data
     * @see #serializeToFile(StoreSerializationStrategy, String)
     * @see #deserializeFromFile(StoreSerializationStrategy, Path)
     */
    public MemFileEmbeddingStore<Embedded> deserializeFromFile(
            StoreSerializationStrategy<Embedded> storeSerializationStrategy, String filePath) {
        return storeSerializationStrategy.deserializeFromFile(filePath);
    }

    /**
     * Saves the provided embedded content to a chunk file within the configured
     * {@link #chunkStorageDirectory}.
     * <p>
     * The content is serialized to JSON using the following rules:
     * <ul>
     * <li>If the object is a {@link dev.langchain4j.data.segment.TextSegment}, it
     * is stored in a custom {@link ChunkData} JSON format containing the text and
     * its optional metadata (as a map).</li>
     * <li>For all other object types, default Jackson serialization is used.</li>
     * </ul>
     *
     *
     * <p>
     * The file is named using the given {@code id} followed by a {@code .json}
     * extension. If a file with the same name already exists, it is replaced.
     *
     *
     * <p>
     * On successful save, the method returns the relative filename (not the full
     * path), which can later be used to reload the content via
     * {@link #loadChunkFromFile(String)}.
     *
     *
     * @param id       the unique identifier for the embedded content; must not be
     *                 blank
     * @param embedded the embedded content to save; must not be {@code null}
     * @return the relative filename of the saved chunk (e.g.,
     *         {@code &lt;id&gt;.json})
     * @throws RuntimeException if the file cannot be written
     */
    private String saveChunkToFile(String id, Embedded embedded) {
        try {
            String fileName = id + ".json";
            Path filePath = chunkStorageDirectory.resolve(fileName);

            String content;
            if (embedded instanceof TextSegment) {
                // Custom serialization for TextSegment
                TextSegment textSegment = (TextSegment) embedded;
                ChunkData chunkData = new ChunkData(
                        textSegment.text(),
                        textSegment.metadata() != null ? textSegment.metadata().toMap() : null);
                content = OBJECT_MAPPER.writeValueAsString(chunkData);
            } else {
                // For other types, try default serialization
                content = OBJECT_MAPPER.writeValueAsString(embedded);
            }

            Files.write(filePath, content.getBytes(), CREATE, TRUNCATE_EXISTING);
            log.debug("Saved chunk to file: {}", filePath);
            return fileName;
        } catch (IOException e) {
            log.error("Failed to save chunk to file for id: {}", id, e);
            throw new RuntimeException("Failed to save chunk to file", e);
        }
    }

    /**
     * Loads the embedded content associated with a given chunk file.
     * <p>
     * The chunk file is expected to be located within the configured
     * {@link #chunkStorageDirectory} and contain JSON-serialized data. This method
     * will first attempt to retrieve the content from the in-memory cache before
     * reading from disk.
     *
     *
     * @param chunkFilePath the relative path (filename) of the chunk file within
     *                      the chunk storage directory; may be {@code null}
     * @return the loaded embedded object, or {@code null} if not found or if an
     *         error occurs during deserialization
     */
    @SuppressWarnings("unchecked")
    private Embedded loadChunkFromFile(String chunkFilePath) {
        if (chunkFilePath == null) {
            return null;
        }

        // Check cache first
        String cacheKey = chunkFilePath;
        Embedded cached = chunkCache.get(cacheKey);
        if (cached != null) {
            log.debug("Loaded chunk from cache: {}", chunkFilePath);
            return cached;
        }

        try {
            Path filePath = chunkStorageDirectory.resolve(chunkFilePath);
            if (!Files.exists(filePath)) {
                log.warn("Chunk file does not exist: {}", filePath);
                return null;
            }

            String content = Files.readString(filePath);

            // Try to deserialize as ChunkData first (for TextSegment)
            try {
                ChunkData chunkData = OBJECT_MAPPER.readValue(content, ChunkData.class);
                Metadata metadata = chunkData.metadata != null ? Metadata.from(chunkData.metadata) : null;
                Embedded embedded = (Embedded) TextSegment.from(chunkData.text, metadata);

                // Add to cache
                chunkCache.put(cacheKey, embedded);
                log.debug("Loaded chunk from file: {}", filePath);
                return embedded;
            } catch (Exception e) {
                // Fall back to direct deserialization for other types
                Embedded embedded = (Embedded) OBJECT_MAPPER.readValue(content, TextSegment.class);

                // Add to cache
                chunkCache.put(cacheKey, embedded);
                log.debug("Loaded chunk from file (fallback): {}", filePath);
                return embedded;
            }
        } catch (IOException e) {
            log.error("Failed to load chunk from file: {}", chunkFilePath, e);
            return null;
        }
    }

    private void deleteChunkFile(String chunkFilePath) {
        if (chunkFilePath == null) {
            return;
        }

        try {
            Path filePath = chunkStorageDirectory.resolve(chunkFilePath);
            Files.deleteIfExists(filePath);
            log.debug("Deleted chunk file: {}", filePath);
        } catch (IOException e) {
            log.warn("Failed to delete chunk file: {}", chunkFilePath, e);
        }
    }

    private void createChunkStorageDirectory() {
        try {
            Files.createDirectories(chunkStorageDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create chunk storage directory: " + chunkStorageDirectory, e);
        }
    }

    private static Path createDefaultChunkDirectory() {
        try {
            return Files.createTempDirectory("memfile-embedding-store");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create default chunk storage directory", e);
        }
    }

    private static <K, V> Map<K, V> createLRUCache(int maxSize) {
        // Calculate capacity so that (capacity * loadFactor) >= maxSize
        // This avoids an unnecessary resize before reaching maxSize entries.
        int capacity = (int) Math.ceil(maxSize / 0.75f) + 1;

        return new LinkedHashMap<K, V>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxSize; // Evict eldest when size exceeds maxSize
            }
        };
    }

    public static class Entry<Embedded> {
        String id;

        Embedding embedding;

        String chunkFilePath; // File path relative to chunk storage directory

        @JsonCreator
        Entry(
                @JsonProperty("id") String id,
                @JsonProperty("embedding") Embedding embedding,
                @JsonProperty("chunkFilePath") String chunkFilePath) {
            this.id = ensureNotBlank(id, "id");
            this.embedding = ensureNotNull(embedding, "embedding");
            this.chunkFilePath = chunkFilePath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Entry<?> that = (Entry<?>) o;
            return Objects.equals(this.id, that.id)
                    && Objects.equals(this.embedding, that.embedding)
                    && Objects.equals(this.chunkFilePath, that.chunkFilePath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, embedding, chunkFilePath);
        }

        public String getId() {
            return id;
        }

        public Embedding getEmbedding() {
            return embedding;
        }

        public String getChunkFilePath() {
            return chunkFilePath;
        }
    }

    public static class MemFileStoreData<T> {
        @JsonProperty("entries")
        List<Entry<T>> entries;

        @JsonProperty("chunkStorageDirectory")
        String chunkStorageDirectory;

        @JsonProperty("cacheSize")
        int cacheSize;

        @JsonCreator
        MemFileStoreData(
                @JsonProperty("entries") List<Entry<T>> entries,
                @JsonProperty("chunkStorageDirectory") String chunkStorageDirectory,
                @JsonProperty("cacheSize") int cacheSize) {
            this.entries = entries != null ? entries : new ArrayList<>();
            this.chunkStorageDirectory = chunkStorageDirectory;
            this.cacheSize = cacheSize;
        }

        // Constructor for generic entries - converts to TextSegment entries
        @SuppressWarnings("unchecked")
        MemFileStoreData(Collection<? extends Entry<?>> genericEntries, String chunkStorageDirectory, int cacheSize) {
            this.entries = new ArrayList<>();
            for (Entry<?> entry : genericEntries) {
                this.entries.add((Entry<T>) entry);
            }
            this.chunkStorageDirectory = chunkStorageDirectory;
            this.cacheSize = cacheSize;
        }

        public List<Entry<T>> getEntries() {
            return entries;
        }

        public String getChunkStorageDirectory() {
            return chunkStorageDirectory;
        }

        public int getCacheSize() {
            return cacheSize;
        }
    }

    // Helper class for TextSegment serialization
    static class ChunkData {
        @JsonProperty("text")
        public String text;

        @JsonProperty("metadata")
        public Map<String, Object> metadata;

        @JsonCreator
        public ChunkData(@JsonProperty("text") String text, @JsonProperty("metadata") Map<String, Object> metadata) {
            this.text = text;
            this.metadata = metadata;
        }
    }
}
