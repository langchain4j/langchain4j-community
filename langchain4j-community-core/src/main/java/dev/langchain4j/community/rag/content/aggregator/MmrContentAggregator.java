package dev.langchain4j.community.rag.content.aggregator;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.community.rag.content.aggregator.strategy.EmbeddingStrategy;
import dev.langchain4j.community.rag.content.aggregator.strategy.EmbeddingStrategyFactory;
import dev.langchain4j.community.store.embedding.MmrSelector;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReciprocalRankFuser;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Refactored MmrContentAggregator using Strategy pattern.
 * Automatically selects the optimal embedding processing strategy based on content analysis,
 * with option for manual strategy override.
 */
public class MmrContentAggregator implements ContentAggregator {

    private static final Logger log = LoggerFactory.getLogger(MmrContentAggregator.class);
    private static final double DEFAULT_LAMBDA = 0.7;

    private static final Function<Map<Query, Collection<List<Content>>>, Query> DEFAULT_QUERY_SELECTOR =
            (queryToContents) -> {
                if (queryToContents.size() > 1) {
                    throw new IllegalArgumentException(String.format(
                            "The 'queryToContents' contains %s queries, making MMR ambiguous. "
                                    + "Please provide a 'querySelector' in the constructor/builder.",
                            queryToContents.size()));
                }
                return queryToContents.keySet().iterator().next();
            };

    private final EmbeddingModel embeddingModel;
    private final ScoringModel scoringModel;
    private final Function<Map<Query, Collection<List<Content>>>, Query> querySelector;
    private final Double minScore;
    private final Integer maxResults;
    private final double lambda;
    private final boolean forceEmbeddingGeneration;
    private final EmbeddingStrategy manualStrategy;

    /**
     * Simple constructor with only EmbeddingModel.
     * Automatically selects optimal embedding strategy.
     */
    public MmrContentAggregator(EmbeddingModel embeddingModel) {
        this(embeddingModel, null, DEFAULT_QUERY_SELECTOR, null, Integer.MAX_VALUE, DEFAULT_LAMBDA, false, null);
    }

    /**
     * Constructor with EmbeddingModel and lambda.
     * Automatically selects optimal embedding strategy.
     */
    public MmrContentAggregator(EmbeddingModel embeddingModel, double lambda) {
        this(embeddingModel, null, DEFAULT_QUERY_SELECTOR, null, Integer.MAX_VALUE, lambda, false, null);
    }

    /**
     * Constructor with EmbeddingModel and forceEmbeddingGeneration flag.
     */
    public MmrContentAggregator(EmbeddingModel embeddingModel, boolean forceEmbeddingGeneration) {
        this(
                embeddingModel,
                null,
                DEFAULT_QUERY_SELECTOR,
                null,
                Integer.MAX_VALUE,
                DEFAULT_LAMBDA,
                forceEmbeddingGeneration,
                null);
    }

    /**
     * Constructor with EmbeddingModel and manual strategy.
     * Uses the provided strategy instead of auto-selection.
     */
    public MmrContentAggregator(EmbeddingModel embeddingModel, EmbeddingStrategy strategy) {
        this(embeddingModel, null, DEFAULT_QUERY_SELECTOR, null, Integer.MAX_VALUE, DEFAULT_LAMBDA, false, strategy);
    }

    /**
     * Full constructor with all parameters.
     */
    public MmrContentAggregator(
            EmbeddingModel embeddingModel,
            ScoringModel scoringModel,
            Function<Map<Query, Collection<List<Content>>>, Query> querySelector,
            Double minScore,
            Integer maxResults,
            double lambda,
            boolean forceEmbeddingGeneration,
            EmbeddingStrategy manualStrategy) {
        this.embeddingModel = (forceEmbeddingGeneration || manualStrategy != null)
                ? embeddingModel
                : ensureNotNull(embeddingModel, "embeddingModel");
        this.scoringModel = scoringModel;
        this.querySelector = getOrDefault(querySelector, DEFAULT_QUERY_SELECTOR);
        this.minScore = minScore;
        this.maxResults = getOrDefault(maxResults, Integer.MAX_VALUE);
        this.lambda = lambda;
        this.forceEmbeddingGeneration = forceEmbeddingGeneration;
        this.manualStrategy = manualStrategy;

        if (forceEmbeddingGeneration && manualStrategy != null) {
            log.warn("Both forceEmbeddingGeneration and manualStrategy provided. Manual strategy takes precedence.");
        }

        if (scoringModel != null) {
            log.warn("ScoringModel provided but hybrid MMR-reranking is not yet implemented. "
                    + "Currently using cosine similarity only. "
                    + "TODO: Implement hybrid approach combining re-ranking scores with MMR diversity.");
        }

        if (manualStrategy != null) {
            log.info(
                    "MMR configured with manual strategy: {}",
                    manualStrategy.getClass().getSimpleName());
        } else if (forceEmbeddingGeneration) {
            log.info("MMR configured to force embedding generation regardless of existing embeddings");
        } else {
            log.info("MMR configured to automatically select optimal embedding strategy based on content analysis");
        }
    }

    public static MmrContentAggregatorBuilder builder() {
        return new MmrContentAggregatorBuilder();
    }

    @Override
    public List<Content> aggregate(Map<Query, Collection<List<Content>>> queryToContents) {
        if (queryToContents.isEmpty()) {
            return Collections.emptyList();
        }

        Query query = querySelector.apply(queryToContents);
        Map<Query, List<Content>> queryToFusedContents = fuse(queryToContents);
        List<Content> fusedContents = ReciprocalRankFuser.fuse(queryToFusedContents.values());

        if (fusedContents.isEmpty()) {
            return fusedContents;
        }

        if (maxResults < Integer.MAX_VALUE && fusedContents.size() < 5 * maxResults) {
            log.warn(
                    "Pre-MMR candidate count is lower than expected: {} items (recommended: 5–10× maxResults, current range: {}–{})",
                    fusedContents.size(),
                    5 * maxResults,
                    10 * maxResults);
        }

        return applyMmr(fusedContents, query);
    }

    private Map<Query, List<Content>> fuse(Map<Query, Collection<List<Content>>> queryToContents) {
        return queryToContents.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> ReciprocalRankFuser.fuse(entry.getValue())));
    }

    private List<Content> applyMmr(List<Content> contents, Query query) {
        // Strategy selection: Manual > ForceGeneration > Auto
        EmbeddingStrategy strategy;
        if (manualStrategy != null) {
            strategy = manualStrategy;
            log.debug("Using manual strategy: {}", strategy.getClass().getSimpleName());
        } else {
            strategy = EmbeddingStrategyFactory.createStrategy(contents, forceEmbeddingGeneration);
        }

        // Process query embedding using selected strategy
        Embedding queryEmbedding = strategy.processQueryEmbedding(query, contents, embeddingModel);

        // Process content embeddings using selected strategy
        List<EmbeddingMatch<Content>> matches = strategy.processContents(contents, queryEmbedding, embeddingModel);

        // Filter by minimum score if specified
        if (minScore != null) {
            matches =
                    matches.stream().filter(match -> match.score() >= minScore).collect(Collectors.toList());
        }

        // Apply MMR selection
        int resultsToSelect = Math.min(maxResults, matches.size());

        return MmrSelector.select(queryEmbedding, matches, resultsToSelect, lambda).stream()
                .map(EmbeddingMatch::embedded)
                .collect(Collectors.toList());
    }

    // Builder class
    public static class MmrContentAggregatorBuilder {
        private EmbeddingModel embeddingModel;
        private ScoringModel scoringModel;
        private Function<Map<Query, Collection<List<Content>>>, Query> querySelector;
        private Double minScore;
        private Integer maxResults;
        private Double lambda;
        private Boolean forceEmbeddingGeneration;
        private EmbeddingStrategy manualStrategy;

        MmrContentAggregatorBuilder() {}

        public MmrContentAggregatorBuilder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        /**
         * Sets the ScoringModel for potential future hybrid MMR implementation.
         * Currently not utilized - logs a warning.
         * TODO: Implement hybrid approach combining re-ranking with MMR.
         */
        public MmrContentAggregatorBuilder scoringModel(ScoringModel scoringModel) {
            this.scoringModel = scoringModel;
            return this;
        }

        public MmrContentAggregatorBuilder querySelector(
                Function<Map<Query, Collection<List<Content>>>, Query> querySelector) {
            this.querySelector = querySelector;
            return this;
        }

        /**
         * Sets the minimum relevance score threshold.
         * Contents with cosine similarity below this value will be filtered out before MMR selection.
         * Note: This filtering happens before MMR diversity calculation, which may reduce
         * the pool of diverse candidates. Use with caution.
         */
        public MmrContentAggregatorBuilder minScore(Double minScore) {
            this.minScore = minScore;
            return this;
        }

        public MmrContentAggregatorBuilder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * Sets the lambda parameter for MMR.
         * 0.0 = maximum diversity (ignore relevance)
         * 1.0 = maximum relevance (ignore diversity)
         * 0.7 = balanced (default, slightly favor relevance)
         */
        public MmrContentAggregatorBuilder lambda(Double lambda) {
            this.lambda = lambda;
            return this;
        }

        /**
         * Sets whether to force embedding generation regardless of existing embeddings.
         * false (default) = Automatically select optimal strategy based on content analysis
         * true = Force GenerateEmbeddings strategy regardless of existing embeddings
         *
         * Note: If manualStrategy is also set, manual strategy takes precedence.
         */
        public MmrContentAggregatorBuilder forceEmbeddingGeneration(Boolean forceEmbeddingGeneration) {
            this.forceEmbeddingGeneration = forceEmbeddingGeneration;
            return this;
        }

        /**
         * Sets a manual embedding strategy to use instead of auto-selection.
         * When set, this takes precedence over forceEmbeddingGeneration and auto-selection.
         *
         * @param strategy The specific strategy to use (GenerateEmbeddings, UseExistingEmbeddings, or HybridEmbeddings)
         */
        public MmrContentAggregatorBuilder strategy(EmbeddingStrategy strategy) {
            this.manualStrategy = strategy;
            return this;
        }

        public MmrContentAggregator build() {
            boolean forceGeneration = getOrDefault(forceEmbeddingGeneration, false);

            // Full parameter constructor
            return new MmrContentAggregator(
                    embeddingModel,
                    scoringModel,
                    querySelector,
                    minScore,
                    maxResults,
                    getOrDefault(lambda, DEFAULT_LAMBDA),
                    forceGeneration,
                    manualStrategy);
        }
    }
}
