package dev.langchain4j.community.store.embedding;

import static dev.langchain4j.store.embedding.CosineSimilarity.between;
import static java.util.Comparator.comparingDouble;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * A utility class that implements the Maximum Marginal Relevance (MMR) algorithm
 * for selecting diverse and relevant results from a list of embedding matches.
 * <p>
 * MMR balances relevance to a query with diversity among the selected results using the formula:
 * MMR = λ × Relevance(candidate, query) - (1-λ) × max(Similarity(candidate, selected))
 * <p>
 * This class is designed to be a low-level, stateless utility that operates on embeddings,
 * making it reusable across different modules and applications.
 */
public final class MmrSelector {

    private static final double INITIAL_MMR_SCORE = -1.0;
    private static final double INITIAL_DIVERSITY_SCORE = 0.0;
    private static final double MIN_LAMBDA = 0.0;
    private static final double MAX_LAMBDA = 1.0;

    private MmrSelector() {}

    /**
     * Selects a subset of embedding matches using the MMR algorithm to balance relevance and diversity.
     *
     * @param queryEmbedding  The embedding of the query for relevance calculation.
     * @param candidates      The list of candidate matches to select from, typically sorted by relevance.
     * @param maxResults      The maximum number of results to return.
     * @param lambda          A value between 0 and 1 (inclusive) that balances relevance and diversity.
     *                        Higher lambda (e.g., 0.7-0.8) prioritizes relevance,
     *                        lower lambda (e.g., 0.3-0.4) prioritizes diversity.
     *                        A value of 1.0 is equivalent to standard relevance-based ranking.
     * @param <T>             The type of the content that has been embedded.
     * @return A new list of selected embedding matches ordered by MMR score.
     * @throws IllegalArgumentException if lambda is not between 0.0 and 1.0 (inclusive),
     *                                  if queryEmbedding is null, or if maxResults is negative.
     */
    public static <T> List<EmbeddingMatch<T>> select(
            Embedding queryEmbedding, List<EmbeddingMatch<T>> candidates, int maxResults, double lambda) {

        validateParameters(queryEmbedding, lambda, maxResults);

        // Handle edge cases
        if (isNull(candidates) || candidates.isEmpty() || maxResults <= 0) {
            return new ArrayList<>();
        }

        // If we need more results than available, return all candidates
        if (candidates.size() <= maxResults) {
            return new ArrayList<>(candidates);
        }

        return performMmrSelection(queryEmbedding, candidates, maxResults, lambda);
    }

    /**
     * Validates the input parameters for the MMR selection.
     */
    private static void validateParameters(Embedding queryEmbedding, double lambda, int maxResults) {
        if (isNull(queryEmbedding)) {
            throw new IllegalArgumentException("Query embedding cannot be null");
        }
        if (lambda < MIN_LAMBDA || lambda > MAX_LAMBDA) {
            throw new IllegalArgumentException(
                    "Lambda must be between " + MIN_LAMBDA + " and " + MAX_LAMBDA + " (inclusive), got: " + lambda);
        }
        if (maxResults < 0) {
            throw new IllegalArgumentException("Max results cannot be negative, got: " + maxResults);
        }
    }

    /**
     * Performs the core MMR selection algorithm.
     */
    private static <T> List<EmbeddingMatch<T>> performMmrSelection(
            Embedding queryEmbedding, List<EmbeddingMatch<T>> candidates, int maxResults, double lambda) {

        List<EmbeddingMatch<T>> selected = new ArrayList<>(maxResults);
        List<EmbeddingMatch<T>> remaining = new ArrayList<>(candidates);

        // Pre-sort candidates by relevance score (descending) for better initial ordering
        remaining.sort(
                comparingDouble((EmbeddingMatch<T> match) -> match.score()).reversed());

        // Iteratively select the best candidate based on MMR score
        while (selected.size() < maxResults && !remaining.isEmpty()) {
            EmbeddingMatch<T> bestCandidate = findBestMmrCandidate(queryEmbedding, remaining, selected, lambda);

            if (nonNull(bestCandidate)) {
                selected.add(bestCandidate);
                remaining.remove(bestCandidate);
            } else {
                // Fallback: select the most relevant remaining candidate
                selected.add(remaining.remove(0));
            }
        }

        return selected;
    }

    /**
     * Finds the candidate with the highest MMR score from the remaining candidates.
     *
     * @param queryEmbedding  The query embedding for relevance calculation.
     * @param remaining       The list of candidates not yet selected.
     * @param selected        The list of candidates already selected.
     * @param lambda          The balance parameter between relevance and diversity.
     * @param <T>             The type of the embedded content.
     * @return The candidate with the highest MMR score, or null if no suitable candidate found.
     */
    private static <T> EmbeddingMatch<T> findBestMmrCandidate(
            Embedding queryEmbedding,
            List<EmbeddingMatch<T>> remaining,
            List<EmbeddingMatch<T>> selected,
            double lambda) {

        double maxMmrScore = INITIAL_MMR_SCORE;
        EmbeddingMatch<T> bestCandidate = null;

        for (EmbeddingMatch<T> candidate : remaining) {
            double relevanceScore = getRelevanceScore(candidate, queryEmbedding);
            double diversityScore = calculateDiversityScore(candidate, selected);
            double mmrScore = calculateMmrScore(relevanceScore, diversityScore, lambda);

            if (mmrScore > maxMmrScore) {
                maxMmrScore = mmrScore;
                bestCandidate = candidate;
            }
        }

        return bestCandidate;
    }

    /**
     * Gets the relevance score for a candidate.
     * Uses the existing score from the candidate if available, otherwise calculates using cosine similarity.
     */
    private static <T> double getRelevanceScore(EmbeddingMatch<T> candidate, Embedding queryEmbedding) {
        // Use existing score if available (most common case)
        if (candidate.score() > 0) {
            return candidate.score();
        }
        // Fallback: calculate using cosine similarity
        return between(candidate.embedding(), queryEmbedding);
    }

    /**
     * Calculates the diversity score for a candidate against already selected items.
     * Returns the maximum similarity with any selected item (higher = less diverse).
     */
    private static <T> double calculateDiversityScore(EmbeddingMatch<T> candidate, List<EmbeddingMatch<T>> selected) {
        if (selected.isEmpty()) {
            return INITIAL_DIVERSITY_SCORE;
        }

        OptionalDouble maxSimilarity = selected.stream()
                .mapToDouble(selectedItem -> between(candidate.embedding(), selectedItem.embedding()))
                .max();

        return maxSimilarity.orElse(INITIAL_DIVERSITY_SCORE);
    }

    /**
     * Calculates the MMR score using the formula:
     * MMR = λ × Relevance - (1-λ) × Diversity
     *
     * @param relevanceScore  The relevance score of the candidate to the query.
     * @param diversityScore  The diversity score (max similarity with selected items).
     * @param lambda          The balance parameter between relevance and diversity.
     * @return The calculated MMR score.
     */
    private static double calculateMmrScore(double relevanceScore, double diversityScore, double lambda) {
        return lambda * relevanceScore - (1.0 - lambda) * diversityScore;
    }
}
