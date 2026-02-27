package dev.langchain4j.community.store.embedding.arcadedb;

import com.arcadedb.database.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utility methods for converting between ArcadeDB documents and LangChain4j types.
 */
final class ArcadeDBEmbeddingUtils {

    static final String PROPERTY_ID = "id";
    static final String PROPERTY_EMBEDDING = "embedding";
    static final String PROPERTY_TEXT = "text";
    static final String PROPERTY_DELETED = "deleted";

    // Properties that are internal to the store/HNSW and should not appear in user metadata
    static final Set<String> RESERVED_PROPERTIES = Set.of(
            PROPERTY_ID, PROPERTY_EMBEDDING, PROPERTY_TEXT, PROPERTY_DELETED,
            "vectorMaxLevel"  // added by HNSW index
    );

    private ArcadeDBEmbeddingUtils() {
    }

    static EmbeddingMatch<TextSegment> toEmbeddingMatch(Document doc, double score, String metadataPrefix) {
        String id = doc.getString(PROPERTY_ID);

        Object rawVector = doc.get(PROPERTY_EMBEDDING);
        Embedding embedding = null;
        if (rawVector instanceof float[] vector) {
            embedding = Embedding.from(vector);
        }

        String text = doc.has(PROPERTY_TEXT) ? doc.getString(PROPERTY_TEXT) : null;
        TextSegment textSegment = null;
        if (text != null) {
            Map<String, Object> metadataMap = extractMetadata(doc, metadataPrefix);
            textSegment = metadataMap.isEmpty()
                    ? TextSegment.from(text)
                    : TextSegment.from(text, Metadata.from(metadataMap));
        }

        return new EmbeddingMatch<>(score, id, embedding, textSegment);
    }

    static Map<String, Object> extractMetadata(Document doc, String metadataPrefix) {
        Map<String, Object> metadata = new HashMap<>();
        for (String prop : doc.getPropertyNames()) {
            if (RESERVED_PROPERTIES.contains(prop)) {
                continue;
            }
            String key;
            if (!metadataPrefix.isEmpty() && prop.startsWith(metadataPrefix)) {
                key = prop.substring(metadataPrefix.length());
            } else if (metadataPrefix.isEmpty()) {
                key = prop;
            } else {
                continue;
            }
            metadata.put(key, doc.get(prop));
        }
        return metadata;
    }

    static float[] embeddingToFloatArray(Embedding embedding) {
        return embedding.vector();
    }
}
