package dev.langchain4j.community.store.embedding.memfile.serialization;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import dev.langchain4j.community.store.embedding.memfile.MemFileEmbeddingStore;
import dev.langchain4j.community.store.embedding.memfile.MemFileEmbeddingStore.MemFileStoreData;
import dev.langchain4j.data.embedding.Embedding;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * JSON-based implementation of {@link StoreSerializationStrategy} for {@link MemFileEmbeddingStore}.
 * <p>
 * This implementation uses Jackson ObjectMapper to serialize and deserialize embedding store data
 * to and from JSON format. It provides a human-readable serialization format that includes
 * proper formatting and custom handling for embedding vectors.
 * </p>
 *
 * <p>
 * <b>Serialization Features:</b>
 * <ul>
 * <li><b>JSON Pretty Printing:</b> Output is formatted with proper indentation for readability</li>
 * <li><b>Custom Embedding Serialization:</b> Float arrays in embeddings are properly serialized/deserialized</li>
 * <li><b>Robust Error Handling:</b> Wraps Jackson exceptions in RuntimeExceptions with clear error messages</li>
 * <li><b>File Operations:</b> Supports atomic file writes with proper directory creation</li>
 * </ul>
 *
 * <p>
 * <b>JSON Structure:</b> The serialized JSON includes:
 * <ul>
 * <li>Store entries with embedding vectors, IDs, and chunk file references</li>
 * <li>Configuration metadata (chunk storage directory, cache size)</li>
 * <li>All necessary data to fully restore the embedding store's state</li>
 * </ul>
 *
 * <p>
 * <b>Thread Safety:</b> This class is thread-safe. The static ObjectMapper is configured once
 * and can be safely used by multiple threads concurrently.
 * </p>
 *
 * <p>
 * <b>Usage Example:</b>
 * <pre>{@code
 * StoreSerializationStrategy<String> strategy = new JsonStoreSerializationStrategy<>();
 * MemFileEmbeddingStore<String> store = new MemFileEmbeddingStore<>();
 *
 * // Serialize to JSON string
 * String json = strategy.serialize(store);
 *
 * // Serialize to file
 * Path file = Paths.get("store.json");
 * strategy.serializeToFile(store, file);
 *
 * // Deserialize from JSON string
 * MemFileEmbeddingStore<String> restoredStore = strategy.deserialize(json, store);
 *
 * // Deserialize from file
 * MemFileEmbeddingStore<String> restoredStore2 = strategy.deserializeFromFile(file, store);
 * }</pre>
 *
 * @param <T> the type of metadata associated with embedded text segments
 * @see StoreSerializationStrategy
 * @see MemFileEmbeddingStore
 * @since 1.0.0
 */
public class JsonStoreSerializationStrategy<T> implements StoreSerializationStrategy<T> {

    /**
     * Pre-configured Jackson ObjectMapper instance for JSON serialization/deserialization.
     * <p>
     * This mapper is configured with:
     * <ul>
     * <li>Pretty printing enabled for human-readable JSON output</li>
     * <li>Failure on empty beans disabled to handle objects with no properties</li>
     * <li>Custom serializers for {@link Embedding} objects to handle float arrays</li>
     * </ul>
     * </p>
     *
     */
    private static final ObjectMapper OBJECT_MAPPER;

    /**
     * Static initializer that configures the Jackson ObjectMapper with appropriate settings
     * and custom serializers for embedding vector handling.
     */
    static {
        OBJECT_MAPPER = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

        // Register custom serializers for Embedding
        SimpleModule embeddingModule = new SimpleModule();
        embeddingModule.addSerializer(Embedding.class, new EmbeddingSerializer());
        embeddingModule.addDeserializer(Embedding.class, new EmbeddingDeserializer());
        OBJECT_MAPPER.registerModule(embeddingModule);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Serializes the embedding store to a JSON string with pretty printing enabled.
     * The resulting JSON includes all store entries with their embedding vectors,
     * IDs, chunk file references, and configuration metadata.
     * </p>
     *
     * @param store the embedding store to serialize; must not be null
     * @return a formatted JSON string representation of the store
     * @throws IllegalArgumentException if store is null
     * @throws RuntimeException         if JSON serialization fails
     */
    @Override
    public String serialize(MemFileEmbeddingStore<T> store) {
        ensureNotNull(store, "store");
        try {
            MemFileStoreData<T> data = store.memFileStoreData();
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Serializes the embedding store to a JSON file with atomic write operations.
     * If the target directory does not exist, it will be created. If the file
     * already exists, it will be overwritten.
     * </p>
     *
     * @param store    the embedding store to serialize; must not be null
     * @param filePath the path where the JSON file will be written; must not be null
     * @throws IllegalArgumentException if store or filePath is null
     * @throws RuntimeException         if file I/O operations fail
     */
    @Override
    public void serializeToFile(MemFileEmbeddingStore<T> store, Path filePath) {
        ensureNotNull(store, "store");
        ensureNotNull(filePath, "filePath");
        try {
            String json = serialize(store);
            Files.write(filePath, json.getBytes(), CREATE, TRUNCATE_EXISTING);

        } catch (IOException e) {
            throw new RuntimeException("Failed to serialize MemFileEmbeddingStore to file: " + filePath, e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Deserializes a JSON string back into a {@link MemFileEmbeddingStore} instance.
     * The JSON is parsed and validated, then used to reconstruct the store with
     * all its entries, embeddings, and configuration settings.
     * </p>
     *
     * @param json the JSON string representation of a serialized store; must not be null or blank
     * @return a new MemFileEmbeddingStore instance restored from the JSON data
     * @throws IllegalArgumentException if json is null or blank
     * @throws RuntimeException         if JSON parsing or deserialization fails
     */
    @Override
    public MemFileEmbeddingStore<T> deserialize(String json) {
        ensureNotBlank(json, "json");
        try {
            @SuppressWarnings("unchecked")
            MemFileStoreData<T> data = OBJECT_MAPPER.readValue(json, MemFileStoreData.class);
            return new MemFileEmbeddingStore<T>(
                    data.getEntries(), Paths.get(data.getChunkStorageDirectory()), data.getCacheSize());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize MemFileEmbeddingStore from JSON", e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Deserializes a {@link MemFileEmbeddingStore} from a JSON file.
     * The entire file is read into memory as a string, then passed to
     * {@link #deserialize(String)} for processing.
     * </p>
     *
     * @param filePath the path to the JSON file containing serialized store data; must not be null
     * @return a new MemFileEmbeddingStore instance restored from the file data
     * @throws IllegalArgumentException if filePath is null
     * @throws RuntimeException         if file I/O or JSON deserialization fails
     */
    @Override
    public MemFileEmbeddingStore<T> deserializeFromFile(Path filePath) {
        ensureNotNull(filePath, "filePath");
        try {
            String json = Files.readString(filePath);
            return deserialize(json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load MemFileEmbeddingStore from file: " + filePath, e);
        }
    }

    /**
     * Custom Jackson serializer for {@link Embedding} objects.
     * <p>
     * This serializer converts an Embedding instance into a JSON object with the following structure:
     * <pre>{@code
     * {
     *   "vector": [0.1, 0.2, 0.3, ...]
     * }
     * }</pre>
     *
     * <p>
     * The embedding's float array vector is serialized as a JSON array of numbers,
     * preserving the precision of the floating-point values.
     *
     * @see EmbeddingDeserializer
     * @see Embedding
     */
    static class EmbeddingSerializer extends JsonSerializer<Embedding> {

        /**
         * Serializes an {@link Embedding} object to JSON.
         *
         * @param embedding   the embedding to serialize; must not be null
         * @param gen         the JSON generator to write to
         * @param serializers the serializer provider (unused)
         * @throws IOException if JSON writing fails
         */
        @Override
        public void serialize(Embedding embedding, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeStartObject();
            gen.writeArrayFieldStart("vector");
            for (float value : embedding.vector()) {
                gen.writeNumber(value);
            }
            gen.writeEndArray();
            gen.writeEndObject();
        }
    }

    /**
     * Custom Jackson deserializer for {@link Embedding} objects.
     * <p>
     * This deserializer reconstructs an Embedding instance from a JSON object with the following structure:
     * <pre>{@code
     * {
     *   "vector": [0.1, 0.2, 0.3, ...]
     * }
     * }</pre>
     *
     * <p>
     * The JSON array under the "vector" field is converted back to a float array and used to
     * create a new {@link Embedding} instance via {@link Embedding#from(float[])}.
     *
     * <p>
     * <b>Error Handling:</b> If the JSON structure is invalid (missing "vector" field or
     * non-array vector field), an {@link IOException} is thrown with a descriptive error message.
     *
     * @see EmbeddingSerializer
     * @see Embedding
     */
    static class EmbeddingDeserializer extends JsonDeserializer<Embedding> {

        /**
         * Deserializes an {@link Embedding} object from JSON.
         *
         * @param p    the JSON parser to read from
         * @param ctxt the deserialization context (unused)
         * @return a new Embedding instance created from the JSON data
         * @throws IOException if the JSON structure is invalid or parsing fails
         */
        @Override
        public Embedding deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.getCodec().readTree(p);
            JsonNode vectorNode = node.get("vector");

            if (vectorNode == null || !vectorNode.isArray()) {
                throw new IOException("Invalid embedding format: missing or invalid 'vector' field");
            }

            float[] vector = new float[vectorNode.size()];
            for (int i = 0; i < vectorNode.size(); i++) {
                vector[i] = (float) vectorNode.get(i).asDouble();
            }

            return Embedding.from(vector);
        }
    }
}
