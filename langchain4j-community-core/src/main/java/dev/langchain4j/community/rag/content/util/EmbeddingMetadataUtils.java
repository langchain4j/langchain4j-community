package dev.langchain4j.community.rag.content.util;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.HashMap;
import java.util.Map;

public final class EmbeddingMetadataUtils {

    private static final String DOCUMENT_EMBEDDING_KEY = "embedding";
    private static final String QUERY_EMBEDDING_KEY = "queryEmbedding";

    private EmbeddingMetadataUtils() {}

    public static TextSegment enrichSegmentWithEmbeddings(
            TextSegment segment, Embedding queryEmbedding, Embedding documentEmbedding) {
        Map<String, Object> metadata = new HashMap<>();
        if (segment.metadata() != null) {
            metadata.putAll(segment.metadata().toMap());
        }
        metadata.put(DOCUMENT_EMBEDDING_KEY, documentEmbedding);
        metadata.put(QUERY_EMBEDDING_KEY, queryEmbedding);

        return TextSegment.from(segment.text(), Metadata.from(metadata));
    }

    public static Embedding extractDocumentEmbedding(TextSegment segment) {
        if (segment.metadata() == null) {
            return null;
        }

        Object embedding = segment.metadata().toMap().get(DOCUMENT_EMBEDDING_KEY);
        return embedding instanceof Embedding ? (Embedding) embedding : null;
    }

    public static Embedding extractQueryEmbedding(TextSegment segment) {
        if (segment.metadata() == null) {
            return null;
        }

        Object embedding = segment.metadata().toMap().get(QUERY_EMBEDDING_KEY);
        return embedding instanceof Embedding ? (Embedding) embedding : null;
    }

    public static boolean hasDocumentEmbedding(TextSegment segment) {
        return extractDocumentEmbedding(segment) != null;
    }

    public static boolean hasQueryEmbedding(TextSegment segment) {
        return extractQueryEmbedding(segment) != null;
    }
}
