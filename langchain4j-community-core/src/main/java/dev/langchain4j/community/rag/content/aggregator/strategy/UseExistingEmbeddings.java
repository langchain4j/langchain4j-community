package dev.langchain4j.community.rag.content.aggregator.strategy;

import static dev.langchain4j.store.embedding.CosineSimilarity.between;

import dev.langchain4j.community.rag.content.util.EmbeddingMetadataUtils;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for using all existing embeddings from content metadata.
 * Used when all contents have embeddings available.
 */
public final class UseExistingEmbeddings implements EmbeddingStrategy {

    private static final Logger log = LoggerFactory.getLogger(UseExistingEmbeddings.class);
    private static final String TEMP_EMBEDDING_ID_PREFIX = "mmr-content-";

    @Override
    public Embedding processQueryEmbedding(Query query, List<Content> contents, EmbeddingModel embeddingModel) {
        if (contents.isEmpty()) {
            throw new IllegalStateException("Cannot extract query embedding from empty content list");
        }

        Embedding queryEmbedding =
                EmbeddingMetadataUtils.extractQueryEmbedding(contents.get(0).textSegment());

        if (queryEmbedding == null) {
            throw new IllegalStateException(
                    "Query embedding not found in content metadata. "
                            + "Ensure retriever is properly enriching content with query embeddings using EmbeddingMetadataUtils.");
        }

        log.debug("Using existing query embedding from content metadata");
        return queryEmbedding;
    }

    @Override
    public List<EmbeddingMatch<Content>> processContents(
            List<Content> contents, Embedding queryEmbedding, EmbeddingModel embeddingModel) {

        log.debug("Processing {} contents with existing embeddings", contents.size());

        return contents.stream()
                .map(content -> convertToEmbeddingMatch(content, queryEmbedding))
                .collect(Collectors.toList());
    }

    private EmbeddingMatch<Content> convertToEmbeddingMatch(Content content, Embedding queryEmbedding) {
        Embedding contentEmbedding = extractEmbeddingFromContent(content);
        double score = between(contentEmbedding, queryEmbedding);
        String embeddingId = getEmbeddingId(content);

        return new EmbeddingMatch<>(score, embeddingId, contentEmbedding, content);
    }

    private Embedding extractEmbeddingFromContent(Content content) {
        Embedding embedding = EmbeddingMetadataUtils.extractDocumentEmbedding(content.textSegment());

        if (embedding == null) {
            throw new IllegalStateException("Content must have document embedding for MMR processing. "
                    + "Ensure retriever is properly enriching content with document embeddings using EmbeddingMetadataUtils. "
                    + "Content: "
                    + content.textSegment()
                            .text()
                            .substring(
                                    0,
                                    Math.min(100, content.textSegment().text().length())));
        }

        return embedding;
    }

    private String getEmbeddingId(Content content) {
        Object embeddingId = content.metadata().get(ContentMetadata.EMBEDDING_ID);
        if (embeddingId instanceof String && !((String) embeddingId).isBlank()) {
            return (String) embeddingId;
        }
        return TEMP_EMBEDDING_ID_PREFIX + Math.abs(content.hashCode());
    }
}
