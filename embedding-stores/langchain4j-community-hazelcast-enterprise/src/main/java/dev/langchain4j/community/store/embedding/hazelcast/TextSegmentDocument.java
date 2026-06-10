package dev.langchain4j.community.store.embedding.hazelcast;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Serializable value type stored inside each {@link com.hazelcast.vector.VectorDocument}.
 * <p>
 * Holds the optional text and metadata of a {@link TextSegment} in a form that
 * Hazelcast can serialise across cluster members using standard Java serialisation.
 * Null metadata values are stripped on construction to avoid deserialisation issues.
 */
public class TextSegmentDocument implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String text;
    private final Map<String, Object> metadata;

    private TextSegmentDocument(String text, Map<String, Object> metadata) {
        this.text = text;
        this.metadata = metadata;
    }

    /**
     * Create a {@link TextSegmentDocument} from a {@link TextSegment}.
     * If {@code segment} is {@code null}, an empty document is returned.
     *
     * @param segment the segment to convert, or {@code null}
     * @return a {@link TextSegmentDocument}
     */
    public static TextSegmentDocument from(TextSegment segment) {
        if (segment == null) {
            return new TextSegmentDocument(null, Collections.emptyMap());
        }
        Map<String, Object> meta = new HashMap<>(segment.metadata().toMap());
        meta.entrySet().removeIf(e -> e.getValue() == null);
        return new TextSegmentDocument(segment.text(), meta);
    }

    /**
     * Convert back to a {@link TextSegment}, or {@code null} if this document has no text.
     *
     * @return a {@link TextSegment}, or {@code null}
     */
    public TextSegment toTextSegment() {
        if (text == null) {
            return null;
        }
        return new TextSegment(text, Metadata.from(metadata));
    }
}
