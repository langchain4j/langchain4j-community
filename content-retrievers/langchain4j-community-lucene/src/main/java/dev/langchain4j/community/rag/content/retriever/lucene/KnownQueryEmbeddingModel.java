package dev.langchain4j.community.rag.content.retriever.lucene;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.List;

/**
 * Adapter to support LuceneEmbeddingStore searches. *
 */
class KnownQueryEmbeddingModel implements EmbeddingModel {

    private final Embedding embedding;

    KnownQueryEmbeddingModel(Embedding embedding) {
        this.embedding = ensureNotNull(embedding, "embedding");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Response<Embedding> embed(String text) {
        if (text != null) {
            throw new IllegalArgumentException("Expecting no text");
        }
        return Response.from(embedding);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        throw new UnsupportedOperationException("Not supported for the LuceneEmbeddingStore adapter use case");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        throw new UnsupportedOperationException("Not supported for the LuceneEmbeddingStore adapter use case");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int dimension() {
        return embedding.dimension();
    }
}
