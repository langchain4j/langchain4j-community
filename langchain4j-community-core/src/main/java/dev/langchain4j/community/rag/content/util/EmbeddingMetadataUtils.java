package dev.langchain4j.community.rag.content.util;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for storing and retrieving embeddings in TextSegment metadata.
 *
 * Since metadata only supports specific value types (String, UUID, primitives and their wrappers),
 * embeddings are serialized to Base64 strings for storage and deserialized back to Embedding objects
 * when retrieved.
 */
public final class EmbeddingMetadataUtils {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingMetadataUtils.class);

    private static final String DOCUMENT_EMBEDDING_KEY = "embedding";
    private static final String QUERY_EMBEDDING_KEY = "queryEmbedding";

    private EmbeddingMetadataUtils() {}

    /**
     * Enriches a TextSegment with query and document embeddings stored in metadata.
     *
     * Since metadata only supports limited value types (String, UUID, int, Integer, long, Long,
     * float, Float, double, Double), embeddings are converted to Base64 strings for storage.
     *
     * @param segment the original text segment
     * @param queryEmbedding the embedding of the query (can be null)
     * @param documentEmbedding the embedding of the document (can be null)
     * @return a new TextSegment with embeddings stored in metadata
     */
    public static TextSegment enrichSegmentWithEmbeddings(
            TextSegment segment, Embedding queryEmbedding, Embedding documentEmbedding) {

        Map<String, Object> metadata = new HashMap<>();
        if (segment.metadata() != null) {
            metadata.putAll(segment.metadata().toMap());
        }

        // Only add embeddings to metadata if they are not null
        if (documentEmbedding != null) {
            metadata.put(DOCUMENT_EMBEDDING_KEY, embeddingToBase64(documentEmbedding));
        }
        if (queryEmbedding != null) {
            metadata.put(QUERY_EMBEDDING_KEY, embeddingToBase64(queryEmbedding));
        }

        return TextSegment.from(segment.text(), Metadata.from(metadata));
    }

    public static Embedding extractDocumentEmbedding(TextSegment segment) {
        return extractEmbedding(segment, DOCUMENT_EMBEDDING_KEY);
    }

    public static Embedding extractQueryEmbedding(TextSegment segment) {
        return extractEmbedding(segment, QUERY_EMBEDDING_KEY);
    }

    private static Embedding extractEmbedding(TextSegment segment, String key) {
        if (segment.metadata() == null) {
            return null;
        }
        Object stored = segment.metadata().toMap().get(key);
        if (stored instanceof String base64) {
            return base64ToEmbedding(base64);
        }
        return null;
    }

    /**
     * Converts an Embedding to a Base64 string for metadata storage.
     *
     * This is necessary because metadata only supports specific value types:
     * String, UUID, int, Integer, long, Long, float, Float, double, Double.
     * Embedding objects are not supported, so we serialize the float vector
     * to a Base64 string.
     *
     * @param embedding the embedding to convert (can be null)
     * @return Base64 encoded string representation, or null if embedding is null
     */
    private static String embeddingToBase64(Embedding embedding) {
        log.warn("Document embedding stored as base64 string due to metadata type constraints."
                + " See dev.langchain4j.data.document.Metadata for supported types");
        if (embedding == null) {
            return null;
        }

        float[] vector = embedding.vector();
        if (vector == null) {
            return null;
        }

        ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES * vector.length);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    /**
     * Converts a Base64 string back to an Embedding object.
     *
     * @param base64 the Base64 encoded embedding (can be null)
     * @return the reconstructed Embedding, or null if base64 is null
     */
    private static Embedding base64ToEmbedding(String base64) {
        log.warn("Converting base64 string back to embedding due to metadata type limitations."
                + "See dev.langchain4j.data.document.Metadata for supported types");
        if (base64 == null) {
            return null;
        }

        byte[] bytes = Base64.getDecoder().decode(base64);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        float[] vector = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return new Embedding(vector);
    }

    public static boolean hasDocumentEmbedding(TextSegment segment) {
        return extractDocumentEmbedding(segment) != null;
    }

    public static boolean hasQueryEmbedding(TextSegment segment) {
        return extractQueryEmbedding(segment) != null;
    }
}
