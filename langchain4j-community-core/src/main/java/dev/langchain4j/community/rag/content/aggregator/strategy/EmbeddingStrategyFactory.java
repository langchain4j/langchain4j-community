package dev.langchain4j.community.rag.content.aggregator.strategy;

import dev.langchain4j.community.rag.content.util.EmbeddingMetadataUtils;
import dev.langchain4j.rag.content.Content;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for creating appropriate EmbeddingStrategy based on content analysis.
 * Automatically selects the optimal strategy based on embedding availability.
 */
public class EmbeddingStrategyFactory {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingStrategyFactory.class);

    /**
     * Creates the appropriate embedding strategy based on content analysis.
     *
     * @param contents List of contents to analyze
     * @param forceGeneration If true, forces GenerateEmbeddings strategy regardless of content state
     * @return Optimal EmbeddingStrategy for the given contents
     */
    public static EmbeddingStrategy createStrategy(List<Content> contents, boolean forceGeneration) {
        if (forceGeneration) {
            log.debug("Force generation enabled - using GenerateEmbeddings strategy");
            return new GenerateEmbeddings();
        }

        if (contents.isEmpty()) {
            log.debug("Empty content list - using GenerateEmbeddings strategy");
            return new GenerateEmbeddings();
        }

        // Analyze content embedding availability
        long embeddedCount = contents.stream()
                .mapToLong(content -> hasEmbedding(content) ? 1 : 0)
                .sum();

        if (embeddedCount == 0) {
            log.debug("No embeddings found - using GenerateEmbeddings strategy");
            return new GenerateEmbeddings();
        } else if (embeddedCount == contents.size()) {
            log.debug("All contents have embeddings - using UseExistingEmbeddings strategy");
            return new UseExistingEmbeddings();
        } else {
            log.debug(
                    "Mixed embedding availability ({}/{}) - using HybridEmbeddings strategy",
                    embeddedCount,
                    contents.size());
            return new HybridEmbeddings();
        }
    }

    private static boolean hasEmbedding(Content content) {
        return EmbeddingMetadataUtils.extractDocumentEmbedding(content.textSegment()) != null;
    }
}
