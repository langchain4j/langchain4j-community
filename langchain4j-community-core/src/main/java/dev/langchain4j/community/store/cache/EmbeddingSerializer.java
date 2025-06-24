package dev.langchain4j.community.store.cache;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import dev.langchain4j.data.embedding.Embedding;
import java.io.IOException;

/**
 * Custom JSON serializer for Embedding objects used in the embedding cache.
 * This class converts Embedding objects into a JSON representation for storage in Redis.
 *
 * <p>The JSON output format has a "vector" field containing an array of the embedding's vector values:
 * <pre>
 * {
 *   "vector": [0.1, 0.2, 0.3, ...]
 * }
 * </pre>
 */
public class EmbeddingSerializer extends StdSerializer<Embedding> {

    /**
     * Creates a new EmbeddingSerializer instance.
     * Default constructor required by Jackson.
     */
    public EmbeddingSerializer() {
        this(null);
    }

    /**
     * Creates a new EmbeddingSerializer instance with a specified class.
     *
     * @param t The class of objects this serializer handles
     */
    public EmbeddingSerializer(Class<Embedding> t) {
        super(t);
    }

    @Override
    public void serialize(Embedding embedding, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        gen.writeArrayFieldStart("vector");
        for (float value : embedding.vector()) {
            gen.writeNumber(value);
        }
        gen.writeEndArray();
        gen.writeEndObject();
    }
}
