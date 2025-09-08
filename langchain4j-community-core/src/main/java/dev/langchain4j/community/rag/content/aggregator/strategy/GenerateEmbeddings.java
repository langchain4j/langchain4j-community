package dev.langchain4j.community.rag.content.aggregator.strategy;

import static dev.langchain4j.store.embedding.CosineSimilarity.between;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for generating all embeddings from scratch.
 * Used when no embeddings are available in content metadata.
 */
public final class GenerateEmbeddings implements EmbeddingStrategy {

    private static final Logger log = LoggerFactory.getLogger(GenerateEmbeddings.class);
    private static final String TEMP_EMBEDDING_ID_PREFIX = "mmr-content-";

    @Override
    public Embedding processQueryEmbedding(Query query, List<Content> contents, EmbeddingModel embeddingModel) {
        log.debug(
                "Generating query embedding for: {}",
                query.text().substring(0, Math.min(50, query.text().length())));
        return embeddingModel.embed(query.text()).content();
    }

    @Override
    public List<EmbeddingMatch<Content>> processContents(
            List<Content> contents, Embedding queryEmbedding, EmbeddingModel embeddingModel) {

        log.debug("Generating embeddings for {} contents using embedAll", contents.size());

        List<TextSegment> textSegments =
                contents.stream().map(Content::textSegment).collect(Collectors.toList());

        List<Embedding> embeddings = embeddingModel.embedAll(textSegments).content();

        List<EmbeddingMatch<Content>> matches = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            Content content = contents.get(i);
            Embedding embedding = embeddings.get(i);
            double score = between(embedding, queryEmbedding);
            String embeddingId = getEmbeddingId(content);
            matches.add(new EmbeddingMatch<>(score, embeddingId, embedding, content));
        }

        return matches;
    }

    private String getEmbeddingId(Content content) {
        Object embeddingId = content.metadata().get(ContentMetadata.EMBEDDING_ID);
        if (embeddingId instanceof String && !((String) embeddingId).isBlank()) {
            return (String) embeddingId;
        }
        return TEMP_EMBEDDING_ID_PREFIX + Math.abs(content.hashCode());
    }
}
