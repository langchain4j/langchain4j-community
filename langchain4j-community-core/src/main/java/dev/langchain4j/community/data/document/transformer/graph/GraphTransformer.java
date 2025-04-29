package dev.langchain4j.community.data.document.transformer.graph;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.Experimental;
import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.data.document.Document;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Defines the interface for transforming documents into graph-based documents
 *
 * @since 1.0.0-beta4
 */
@Experimental
public interface GraphTransformer {

    /**
     * Transforms a provided document into a graph-based document.
     *
     * @param document The document to be transformed.
     * @return The transformed graph-based document, or null if the document should be filtered out.
     */
    GraphDocument transform(Document document);

    /**
     * Transforms all the provided documents.
     *
     * @param documents A list of documents to be transformed.
     * @return A list of transformed graph-based documents. The length of this list may be shorter or longer than the original list. Returns an empty list if all documents were filtered out.
     */
    default List<GraphDocument> transformAll(Collection<Document> documents) {
        if (isNullOrEmpty(documents)) {
            return new ArrayList<>();
        }

        return documents.stream().map(this::transform).filter(Objects::nonNull).collect(toList());
    }
}
