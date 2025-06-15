package dev.langchain4j.community.store.cache;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import dev.langchain4j.data.embedding.Embedding;
import java.io.IOException;

/**
 * Custom JSON deserializer for Embedding objects used in the Redis embedding cache.
 * This class converts JSON representations back into Embedding objects, allowing for
 * storage and retrieval of embeddings in Redis using JSON serialization.
 *
 * <p>The expected JSON format has a "vector" field containing an array of floating-point values:
 * <pre>
 * {
 *   "vector": [0.1, 0.2, 0.3, ...]
 * }
 * </pre>
 */
public class EmbeddingDeserializer extends StdDeserializer<Embedding> {

    /**
     * Creates a new EmbeddingDeserializer instance.
     * Default constructor required by Jackson.
     */
    public EmbeddingDeserializer() {
        this(null);
    }

    /**
     * Creates a new EmbeddingDeserializer instance with a specified class.
     *
     * @param vc The value class to deserialize
     */
    public EmbeddingDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public Embedding deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);

        if (node.has("vector") && node.get("vector").isArray()) {
            JsonNode vectorNode = node.get("vector");
            float[] vector = new float[vectorNode.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) vectorNode.get(i).asDouble();
            }
            return Embedding.from(vector);
        }

        throw new IOException("Invalid Embedding format: " + node);
    }
}
