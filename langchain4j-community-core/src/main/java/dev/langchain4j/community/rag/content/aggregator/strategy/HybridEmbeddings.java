package dev.langchain4j.community.rag.content.aggregator.strategy;

import static dev.langchain4j.store.embedding.CosineSimilarity.between;

import dev.langchain4j.community.rag.content.util.EmbeddingMetadataUtils;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for handling mixed embedding availability.
 * Used when some contents have embeddings while others don't.
 */
public final class HybridEmbeddings implements EmbeddingStrategy {

    private static final Logger log = LoggerFactory.getLogger(HybridEmbeddings.class);
    private static final String TEMP_EMBEDDING_ID_PREFIX = "mmr-content-";

    @Override
    public Embedding processQueryEmbedding(Query query, List<Content> contents, EmbeddingModel embeddingModel) {
        // Try to extract from contents first
        Optional<Embedding> existingQueryEmbedding = contents.stream()
                .map(content -> EmbeddingMetadataUtils.extractQueryEmbedding(content.textSegment()))
                .filter(Objects::nonNull)
                .findFirst();

        if (existingQueryEmbedding.isPresent()) {
            log.debug("Using existing query embedding from content metadata");
            return existingQueryEmbedding.get();
        }

        // Generate if not available
        log.debug("Generating query embedding as not found in content metadata");
        return embeddingModel.embed(query.text()).content();
    }

    @Override
    public List<EmbeddingMatch<Content>> processContents(
            List<Content> contents, Embedding queryEmbedding, EmbeddingModel embeddingModel) {

        // Partition contents based on embedding availability
        Map<Boolean, List<Content>> partitioned =
                contents.stream().collect(Collectors.partitioningBy(this::hasEmbedding));

        List<Content> withEmbeddings = partitioned.get(true);
        List<Content> withoutEmbeddings = partitioned.get(false);

        List<EmbeddingMatch<Content>> matches = new ArrayList<>();

        // Process contents with existing embeddings
        if (!withEmbeddings.isEmpty()) {
            log.debug("Processing {} contents with existing embeddings", withEmbeddings.size());
            matches.addAll(processExistingEmbeddings(withEmbeddings, queryEmbedding));
        }

        // Generate embeddings for contents without them
        if (!withoutEmbeddings.isEmpty()) {
            log.debug("Generating embeddings for {} contents", withoutEmbeddings.size());
            matches.addAll(generateMissingEmbeddings(withoutEmbeddings, queryEmbedding, embeddingModel));
        }

        log.debug(
                "Processed {} total contents ({} existing, {} generated)",
                contents.size(),
                withEmbeddings.size(),
                withoutEmbeddings.size());

        return matches;
    }

    private boolean hasEmbedding(Content content) {
        return EmbeddingMetadataUtils.extractDocumentEmbedding(content.textSegment()) != null;
    }

    private List<EmbeddingMatch<Content>> processExistingEmbeddings(List<Content> contents, Embedding queryEmbedding) {

        return contents.stream()
                .map(content -> {
                    Embedding contentEmbedding = EmbeddingMetadataUtils.extractDocumentEmbedding(content.textSegment());
                    double score = between(contentEmbedding, queryEmbedding);
                    String embeddingId = getEmbeddingId(content);
                    return new EmbeddingMatch<>(score, embeddingId, contentEmbedding, content);
                })
                .collect(Collectors.toList());
    }

    private List<EmbeddingMatch<Content>> generateMissingEmbeddings(
            List<Content> contents, Embedding queryEmbedding, EmbeddingModel embeddingModel) {

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
        if (embeddingId instanceof String string && !string.isBlank()) {
            return string;
        }
        return TEMP_EMBEDDING_ID_PREFIX + Math.abs(content.hashCode());
    }
}
