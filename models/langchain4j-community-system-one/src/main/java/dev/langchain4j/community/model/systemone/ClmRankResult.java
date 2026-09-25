package dev.langchain4j.community.model.systemone;

import dev.langchain4j.Experimental;
import java.util.List;

/** CLM's separate candidate ranking result. */
@Experimental
public record ClmRankResult(String model, List<RankedCandidate> ranked) {
    public ClmRankResult {
        ranked = List.copyOf(ranked);
    }

    public record RankedCandidate(int rank, String candidate, double probability) {}
}
