package dev.langchain4j.community.store.cache.embedding.redis;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import dev.langchain4j.data.embedding.Embedding;
import java.io.IOException;

/**
 * Custom JSON deserializer for Embedding objects.
 */
public class EmbeddingDeserializer extends StdDeserializer<Embedding> {

    public EmbeddingDeserializer() {
        this(null);
    }

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
            return new Embedding(vector);
        }

        throw new IOException("Invalid Embedding format: " + node.toString());
    }
}
