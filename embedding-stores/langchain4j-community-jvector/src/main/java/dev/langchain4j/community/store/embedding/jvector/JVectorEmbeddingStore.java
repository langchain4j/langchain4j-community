package dev.langchain4j.community.store.embedding.jvector;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.github.jbellis.jvector.disk.SimpleMappedReader;
import io.github.jbellis.jvector.graph.GraphIndex;
import io.github.jbellis.jvector.graph.GraphIndexBuilder;
import io.github.jbellis.jvector.graph.GraphSearcher;
import io.github.jbellis.jvector.graph.ListRandomAccessVectorValues;
import io.github.jbellis.jvector.graph.RandomAccessVectorValues;
import io.github.jbellis.jvector.graph.SearchResult;
import io.github.jbellis.jvector.graph.disk.OnDiskGraphIndex;
import io.github.jbellis.jvector.graph.similarity.BuildScoreProvider;
import io.github.jbellis.jvector.graph.similarity.SearchScoreProvider;
import io.github.jbellis.jvector.util.Bits;
import io.github.jbellis.jvector.vector.VectorSimilarityFunction;
import io.github.jbellis.jvector.vector.VectorizationProvider;
import io.github.jbellis.jvector.vector.types.VectorFloat;
import io.github.jbellis.jvector.vector.types.VectorTypeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Implementation of {@link EmbeddingStore} using <a href="https://github.com/jbellis/jvector">JVector</a>,
 * a pure Java embedded vector search engine.
 *
 * <p>JVector provides high-performance approximate nearest neighbor (ANN) search using graph-based indexing.
 * This implementation supports:
 * <ul>
 *   <li>Fast similarity search with configurable accuracy/performance tradeoffs</li>
 *   <li>Adding and removing embeddings dynamically</li>
 *   <li>Optional persistent storage to disk (in-memory by default)</li>
 *   <li>Does not support metadata filtering during search since jvector doesn't store metadata</li>
 * </ul>
 *
 * <p>Example usage (in-memory):
 * <pre>{@code
 * EmbeddingStore<TextSegment> store = JVectorEmbeddingStore.builder()
 *     .dimension(384)
 *     .maxDegree(16)
 *     .build();
 * }</pre>
 *
 * <p>Example usage (persistent):
 * <pre>{@code
 * EmbeddingStore<TextSegment> store = JVectorEmbeddingStore.builder()
 *     .dimension(384)
 *     .maxDegree(16)
 *     .persistencePath("/path/to/index")
 *     .build();
 *
 * // Save to disk
 * store.save();
 * }</pre>
 */
public class JVectorEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final Logger log = LoggerFactory.getLogger(JVectorEmbeddingStore.class);

    private final int dimension;
    private final int maxDegree;
    private final int beamWidth;
    private final float neighborOverflow;
    private final float alpha;
    private final VectorSimilarityFunction similarityFunction;
    private final String persistencePath;

    // Thread-safe data structures
    private final Map<String, Integer> idToOrdinal;
    private final Map<Integer, StoredEntry> ordinalToEntry;
    private final List<VectorFloat<?>> vectors;
    private final VectorTypeSupport vectorTypeSupport;

    // Index that needs rebuild on modifications (null for on-disk index)
    private volatile GraphIndex index;

    // On-disk index reference (only used when persistencePath is set)
    private volatile OnDiskGraphIndex diskIndex;
    private volatile SimpleMappedReader.Supplier diskIndexSupplier;

    // Lock for index rebuilding
    private final ReentrantReadWriteLock indexLock;

    /**
     * Entry stored for each embedding
     */
    private static class StoredEntry {
        final String id;
        final Embedding embedding;
        final TextSegment textSegment;

        StoredEntry(String id, Embedding embedding, TextSegment textSegment) {
            this.id = id;
            this.embedding = embedding;
            this.textSegment = textSegment;
        }
    }

    private JVectorEmbeddingStore(int dimension, int maxDegree, int beamWidth,
                                  float neighborOverflow, float alpha,
                                  VectorSimilarityFunction similarityFunction,
                                  String persistencePath) {
        this.dimension = dimension;
        this.maxDegree = maxDegree;
        this.beamWidth = beamWidth;
        this.neighborOverflow = neighborOverflow;
        this.alpha = alpha;
        this.similarityFunction = similarityFunction;
        this.persistencePath = persistencePath;

        this.idToOrdinal = new ConcurrentHashMap<>();
        this.ordinalToEntry = new ConcurrentHashMap<>();
        this.vectors = new ArrayList<>();
        this.vectorTypeSupport = VectorizationProvider.getInstance().getVectorTypeSupport();
        this.index = null;
        this.diskIndex = null;
        this.diskIndexSupplier = null;
        this.indexLock = new ReentrantReadWriteLock();

        // Load from disk if persistence is enabled and files exist
        if (persistencePath != null) {
            try {
                loadFromDisk();
            } catch (IOException e) {
                log.warn("Failed to load index from disk at {}: {}", persistencePath, e.getMessage());
                log.debug("Starting with empty index");
            }
        }
    }

    @Override
    public String add(Embedding embedding) {
        String id = randomUUID();
        add(id, embedding);
        return id;
    }

    @Override
    public void add(String id, Embedding embedding) {
        addInternal(id, embedding, null);
    }

    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        addInternal(id, embedding, textSegment);
        return id;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        List<String> ids = new ArrayList<>(embeddings.size());
        for (Embedding embedding : embeddings) {
            ids.add(add(embedding));
        }
        return ids;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        if (embeddings.size() != embedded.size()) {
            throw new IllegalArgumentException("embeddings and embedded lists must have the same size");
        }

        List<String> ids = new ArrayList<>(embeddings.size());
        for (int i = 0; i < embeddings.size(); i++) {
            ids.add(add(embeddings.get(i), embedded.get(i)));
        }
        return ids;
    }

    @Override
    public void addAll(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        if (ids.size() != embeddings.size() || embeddings.size() != embedded.size()) {
            throw new IllegalArgumentException("ids, embeddings and embedded lists must have the same size");
        }

        for (int i = 0; i < ids.size(); i++) {
            addInternal(ids.get(i), embeddings.get(i), embedded.get(i));
        }
    }

    private void addInternal(String id, Embedding embedding, TextSegment textSegment) {
        ensureNotNull(id, "id");
        ensureNotNull(embedding, "embedding");

        if (embedding.dimension() != dimension) {
            throw new IllegalArgumentException(
                String.format("Embedding dimension (%d) does not match store dimension (%d)",
                    embedding.dimension(), dimension)
            );
        }

        indexLock.writeLock().lock();
        try {
            int ordinal = vectors.size();
            VectorFloat<?> vector = toVectorFloat(embedding);
            vectors.add(vector);

            StoredEntry entry = new StoredEntry(id, embedding, textSegment);
            ordinalToEntry.put(ordinal, entry);
            idToOrdinal.put(id, ordinal);

            // Invalidate indexes - will be rebuilt on next search
            index = null;
            closeDiskIndex();

            log.debug("Added embedding with id: {}, ordinal: {}", id, ordinal);
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        if (ordinalToEntry.isEmpty()) {
            return new EmbeddingSearchResult<>(new ArrayList<>());
        }

        // Ensure index is built
        ensureIndexBuilt();

        // Convert query to VectorFloat
        VectorFloat<?> query = toVectorFloat(request.queryEmbedding());

        // Perform search
        indexLock.readLock().lock();
        try {
            GraphIndex searchIndex = (diskIndex != null) ? diskIndex : index;
            GraphSearcher searcher = new GraphSearcher(searchIndex);

            // Get vector provider for scoring
            RandomAccessVectorValues vectorValues;
            if (diskIndex != null) {
                // For on-disk index, use the disk index's view which has vectors inline
                vectorValues = diskIndex.getView();
            } else {
                // For in-memory index, use the vectors list
                vectorValues = new ListRandomAccessVectorValues(vectors, dimension);
            }

            SearchScoreProvider scoreProvider = SearchScoreProvider.exact(
                query,
                similarityFunction,
                vectorValues
            );

            SearchResult result = searcher.search(
                scoreProvider,
                request.maxResults(),
                Bits.ALL
            );

            // Convert results to EmbeddingMatch
            List<EmbeddingMatch<TextSegment>> matches = new ArrayList<>();
            for (SearchResult.NodeScore nodeScore : result.getNodes()) {
                // Convert score (similarity function dependent)
                double score = convertScore(nodeScore.score);

                if (score < request.minScore()) {
                    continue;
                }

                StoredEntry entry = ordinalToEntry.get(nodeScore.node);
                if (entry != null) {
                    matches.add(new EmbeddingMatch<>(
                        score,
                        entry.id,
                        entry.embedding,
                        entry.textSegment
                    ));
                }
            }

            log.debug("Search returned {} matches", matches.size());
            return new EmbeddingSearchResult<>(matches);

        } finally {
            indexLock.readLock().unlock();
        }
    }

    @Override
    public void removeAll(Collection<String> ids) {
        ensureNotEmpty(ids, "ids");

        indexLock.writeLock().lock();
        try {
            for (String id : ids) {
                Integer ordinal = idToOrdinal.remove(id);
                if (ordinal != null) {
                    ordinalToEntry.remove(ordinal);
                    log.debug("Removed embedding with id: {}, ordinal: {}", id, ordinal);
                }
            }
            // Invalidate indexes - will be rebuilt on next search
            index = null;
            closeDiskIndex();
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    @Override
    public void removeAll() {
        indexLock.writeLock().lock();
        try {
            idToOrdinal.clear();
            ordinalToEntry.clear();
            vectors.clear();
            index = null;
            closeDiskIndex();
            log.debug("Removed all embeddings");
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    /**
     * Ensures the index is built. This method uses double-checked locking for efficiency.
     */
    private void ensureIndexBuilt() {
        if (index == null && diskIndex == null) {
            indexLock.writeLock().lock();
            try {
                if (index == null && diskIndex == null) {
                    if (persistencePath != null) {
                        // Try to load from disk first
                        try {
                            loadIndexFromDisk();
                        } catch (IOException e) {
                            log.debug("Could not load index from disk, rebuilding: {}", e.getMessage());
                            rebuildIndex();
                        }
                    } else {
                        rebuildIndex();
                    }
                }
            } finally {
                indexLock.writeLock().unlock();
            }
        }
    }

    /**
     * Rebuilds the graph index from the current vectors.
     * Must be called while holding the write lock.
     */
    private void rebuildIndex() {
        if (vectors.isEmpty()) {
            return;
        }

        log.debug("Building index with {} vectors", vectors.size());
        long startTime = System.currentTimeMillis();

        RandomAccessVectorValues vectorValues = new ListRandomAccessVectorValues(vectors, dimension);
        BuildScoreProvider scoreProvider = BuildScoreProvider.randomAccessScoreProvider(
            vectorValues,
            similarityFunction
        );

        try (GraphIndexBuilder builder = new GraphIndexBuilder(
                scoreProvider,
                vectorValues.dimension(),
                maxDegree,
                beamWidth,
                neighborOverflow,
                alpha)) {

            index = builder.build(vectorValues);

            long duration = System.currentTimeMillis() - startTime;
            log.debug("Index built in {} ms", duration);

        } catch (Exception e) {
            throw new RuntimeException("Failed to build JVector index", e);
        }
    }

    /**
     * Converts an Embedding to a VectorFloat.
     */
    private VectorFloat<?> toVectorFloat(Embedding embedding) {
        List<Float> list = embedding.vectorAsList();
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return vectorTypeSupport.createFloatVector(array);
    }

    /**
     * Converts a similarity score to the [0, 1] range expected by LangChain4j.
     * JVector's SearchScoreProvider.exact() already returns scores in [0, 1] for similarity functions,
     * so we just need to ensure they're clamped to that range.
     */
    private double convertScore(float rawScore) {
        // JVector already returns scores in [0, 1] range for similarity functions
        // Just clamp to ensure we're within bounds
        return Math.max(0.0, Math.min(1.0, rawScore));
    }

    /**
     * Saves the index and metadata to disk.
     * Only available when persistencePath is configured.
     *
     * @throws IllegalStateException if persistence is not configured
     * @throws RuntimeException if the save operation fails
     */
    public void save() {
        if (persistencePath == null) {
            throw new IllegalStateException("Cannot save: persistence path not configured");
        }

        indexLock.writeLock().lock();
        try {
            log.info("Saving index to disk at {}", persistencePath);
            long startTime = System.currentTimeMillis();

            // Ensure we have an in-memory index to save
            if (index == null) {
                rebuildIndex();
                if (index == null) {
                    log.error("Unable to save index: no vectors available");
                    throw new IllegalStateException("Cannot save an empty embedding store");
                }
            }

            // Save the graph index
            Path graphPath = Paths.get(persistencePath + ".graph");
            Files.createDirectories(graphPath.getParent());

            RandomAccessVectorValues vectorValues = new ListRandomAccessVectorValues(vectors, dimension);
            OnDiskGraphIndex.write(index, vectorValues, graphPath);

            // Save the metadata
            saveMetadata();

            // Close old disk index and open new one
            closeDiskIndex();
            loadIndexFromDisk();

            long duration = System.currentTimeMillis() - startTime;
            log.info("Index saved to disk in {} ms", duration);

        } catch (IOException e) {
            throw new RuntimeException("Failed to save index to disk", e);
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    /**
     * Loads both the index and metadata from disk.
     * Must be called while holding the write lock.
     */
    private void loadFromDisk() throws IOException {
        Path metadataPath = Paths.get(persistencePath + ".metadata");
        if (!Files.exists(metadataPath)) {
            log.debug("Metadata file does not exist at {}, starting with empty store", metadataPath);
            return;
        }

        log.info("Loading metadata from disk at {}", persistencePath);
        loadMetadata();

        // Rebuild vectors list from loaded entries
        vectors.clear();
        for (int i = 0; i < ordinalToEntry.size(); i++) {
            StoredEntry entry = ordinalToEntry.get(i);
            if (entry != null) {
                vectors.add(toVectorFloat(entry.embedding));
            }
        }

        // Try to load the graph index
        loadIndexFromDisk();
    }

    /**
     * Loads only the graph index from disk.
     * Must be called while holding the write lock.
     */
    private void loadIndexFromDisk() throws IOException {
        Path graphPath = Paths.get(persistencePath + ".graph");
        if (!Files.exists(graphPath)) {
            throw new IOException("Graph file does not exist at " + graphPath);
        }

        log.debug("Loading graph index from disk at {}", graphPath);
        closeDiskIndex();

        diskIndexSupplier = new SimpleMappedReader.Supplier(graphPath);
        diskIndex = OnDiskGraphIndex.load(diskIndexSupplier);
        index = null; // Clear in-memory index when using disk index

        log.debug("Loaded disk index with {} nodes", diskIndex.size());
    }

    /**
     * Saves the metadata (idToOrdinal and ordinalToEntry maps) to disk.
     * Must be called while holding the write lock.
     */
    private void saveMetadata() throws IOException {
        Path metadataPath = Paths.get(persistencePath + ".metadata");
        Files.createDirectories(metadataPath.getParent());

        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(metadataPath.toFile()))) {
            // Write number of entries
            dos.writeInt(ordinalToEntry.size());

            // Write each entry
            for (Map.Entry<Integer, StoredEntry> entry : ordinalToEntry.entrySet()) {
                int ordinal = entry.getKey();
                StoredEntry storedEntry = entry.getValue();

                dos.writeInt(ordinal);
                dos.writeUTF(storedEntry.id);

                // Write embedding
                List<Float> embeddingVector = storedEntry.embedding.vectorAsList();
                dos.writeInt(embeddingVector.size());
                for (Float value : embeddingVector) {
                    dos.writeFloat(value);
                }

                // Write text segment (optional)
                if (storedEntry.textSegment != null) {
                    dos.writeBoolean(true);
                    dos.writeUTF(storedEntry.textSegment.text());
                    // Write metadata if present
                    if (storedEntry.textSegment.metadata() != null) {
                        dos.writeBoolean(true);
                        Map<String, Object> metadata = storedEntry.textSegment.metadata().toMap();
                        dos.writeInt(metadata.size());
                        for (Map.Entry<String, Object> metaEntry : metadata.entrySet()) {
                            dos.writeUTF(metaEntry.getKey());
                            dos.writeUTF(String.valueOf(metaEntry.getValue()));
                        }
                    } else {
                        dos.writeBoolean(false);
                    }
                } else {
                    dos.writeBoolean(false);
                }
            }

            log.debug("Saved {} entries to metadata file", ordinalToEntry.size());
        }
    }

    /**
     * Loads the metadata (idToOrdinal and ordinalToEntry maps) from disk.
     * Must be called while holding the write lock.
     */
    private void loadMetadata() throws IOException {
        Path metadataPath = Paths.get(persistencePath + ".metadata");

        try (DataInputStream dis = new DataInputStream(new FileInputStream(metadataPath.toFile()))) {
            int numEntries = dis.readInt();

            idToOrdinal.clear();
            ordinalToEntry.clear();

            for (int i = 0; i < numEntries; i++) {
                int ordinal = dis.readInt();
                String id = dis.readUTF();

                // Read embedding
                int embeddingSize = dis.readInt();
                float[] embeddingArray = new float[embeddingSize];
                for (int j = 0; j < embeddingSize; j++) {
                    embeddingArray[j] = dis.readFloat();
                }
                Embedding embedding = Embedding.from(embeddingArray);

                // Read text segment (optional)
                TextSegment textSegment = null;
                if (dis.readBoolean()) {
                    String text = dis.readUTF();
                    // Read metadata if present
                    if (dis.readBoolean()) {
                        int metadataSize = dis.readInt();
                        dev.langchain4j.data.document.Metadata metadata = new dev.langchain4j.data.document.Metadata();
                        for (int j = 0; j < metadataSize; j++) {
                            String key = dis.readUTF();
                            String value = dis.readUTF();
                            metadata.put(key, value);
                        }
                        textSegment = TextSegment.from(text, metadata);
                    } else {
                        textSegment = TextSegment.from(text);
                    }
                }

                StoredEntry entry = new StoredEntry(id, embedding, textSegment);
                ordinalToEntry.put(ordinal, entry);
                idToOrdinal.put(id, ordinal);
            }

            log.debug("Loaded {} entries from metadata file", numEntries);
        }
    }

    /**
     * Closes the disk index and releases resources.
     */
    private void closeDiskIndex() {
        if (diskIndexSupplier != null) {
            try {
                if (diskIndex != null) {
                    diskIndex.close();
                }
                diskIndexSupplier.close();
            } catch (IOException e) {
                log.warn("Error closing disk index: {}", e.getMessage());
            }
            diskIndexSupplier = null;
        }
        diskIndex = null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int dimension = 384;
        private int maxDegree = 16;
        private int beamWidth = 100;
        private float neighborOverflow = 1.2f;
        private float alpha = 1.2f;
        private VectorSimilarityFunction similarityFunction = VectorSimilarityFunction.DOT_PRODUCT;
        private String persistencePath = null;

        /**
         * Sets the dimension of the embeddings (required).
         * This must match the dimension of the embeddings you will store.
         *
         * @param dimension the embedding dimension
         * @return this builder
         */
        public Builder dimension(int dimension) {
            if (dimension <= 0) {
                throw new IllegalArgumentException("dimension must be positive");
            }
            this.dimension = dimension;
            return this;
        }

        /**
         * Sets the maximum degree (M parameter) of the graph.
         * This controls the number of connections per node in the graph.
         * Higher values provide better recall but use more memory.
         * Recommended: 16 (default)
         *
         * @param maxDegree the maximum degree
         * @return this builder
         */
        public Builder maxDegree(int maxDegree) {
            if (maxDegree <= 0) {
                throw new IllegalArgumentException("maxDegree must be positive");
            }
            this.maxDegree = maxDegree;
            return this;
        }

        /**
         * Sets the beam width for index construction.
         * Higher values produce better quality indexes but take longer to build.
         * Recommended: 100 (default)
         *
         * @param beamWidth the beam width
         * @return this builder
         */
        public Builder beamWidth(int beamWidth) {
            if (beamWidth <= 0) {
                throw new IllegalArgumentException("beamWidth must be positive");
            }
            this.beamWidth = beamWidth;
            return this;
        }

        /**
         * Sets the neighbor overflow factor.
         * Recommended: 1.2 for in-memory indexes (default), 1.5 for disk-based indexes.
         *
         * @param neighborOverflow the neighbor overflow factor
         * @return this builder
         */
        public Builder neighborOverflow(float neighborOverflow) {
            if (neighborOverflow <= 1.0f) {
                throw new IllegalArgumentException("neighborOverflow must be greater than 1.0");
            }
            this.neighborOverflow = neighborOverflow;
            return this;
        }

        /**
         * Sets the alpha parameter for graph diversity.
         * Recommended: 1.2 for high-dimensional vectors (default), 2.0 for low-dimensional (2D/3D).
         *
         * @param alpha the alpha parameter
         * @return this builder
         */
        public Builder alpha(float alpha) {
            if (alpha <= 0) {
                throw new IllegalArgumentException("alpha must be positive");
            }
            this.alpha = alpha;
            return this;
        }

        /**
         * Sets the similarity function to use for vector comparisons.
         * Options: DOT_PRODUCT (default, fastest for normalized vectors), COSINE, EUCLIDEAN
         *
         * @param similarityFunction the similarity function
         * @return this builder
         */
        public Builder similarityFunction(VectorSimilarityFunction similarityFunction) {
            if (similarityFunction == null) {
                throw new IllegalArgumentException("similarityFunction cannot be null");
            }
            this.similarityFunction = similarityFunction;
            return this;
        }

        /**
         * Sets the path for persistent storage of the index.
         * If set, the index and metadata will be loaded from disk on initialization (if files exist)
         * and can be saved to disk using the save() method.
         * If not set (null), the index will be stored in-memory only.
         *
         * @param persistencePath the base path for storing index files (without extension)
         * @return this builder
         */
        public Builder persistencePath(String persistencePath) {
            this.persistencePath = persistencePath;
            return this;
        }

        /**
         * Builds the JVectorEmbeddingStore instance.
         * If persistencePath is set and files exist at that location, the index will be loaded from disk.
         *
         * @return a new JVectorEmbeddingStore
         */
        public JVectorEmbeddingStore build() {
            return new JVectorEmbeddingStore(
                dimension,
                maxDegree,
                beamWidth,
                neighborOverflow,
                alpha,
                similarityFunction,
                persistencePath
            );
        }
    }
}
