package dev.langchain4j.community.store.cache.embedding.redis;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import dev.langchain4j.data.embedding.Embedding;
import java.io.IOException;

/**
 * Custom JSON serializer for Embedding objects.
 */
public class EmbeddingSerializer extends StdSerializer<Embedding> {

    public EmbeddingSerializer() {
        this(null);
    }

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
