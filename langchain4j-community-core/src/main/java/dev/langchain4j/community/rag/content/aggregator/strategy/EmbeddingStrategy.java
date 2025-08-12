package dev.langchain4j.community.rag.content.aggregator.strategy;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.List;

public sealed interface EmbeddingStrategy permits GenerateEmbeddings, HybridEmbeddings, UseExistingEmbeddings {

    /**
     * Process contents and convert them to EmbeddingMatches
     */
    List<EmbeddingMatch<Content>> processContents(
            List<Content> contents, Embedding queryEmbedding, EmbeddingModel embeddingModel);

    /**
     * Extract or generate query embedding
     */
    Embedding processQueryEmbedding(Query query, List<Content> contents, EmbeddingModel embeddingModel);
}
