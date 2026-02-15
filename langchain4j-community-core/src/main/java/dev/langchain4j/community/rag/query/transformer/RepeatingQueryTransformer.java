package dev.langchain4j.community.rag.query.transformer;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;
import static java.util.Collections.singletonList;

import dev.langchain4j.community.prompt.repetition.PromptRepetitionDecision;
import dev.langchain4j.community.prompt.repetition.PromptRepetitionPolicy;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import java.util.Collection;

/**
 * A {@link QueryTransformer} that repeats query text using a {@link PromptRepetitionPolicy}.
 * <p>
 * It returns a single transformed query and preserves metadata.
 */
public class RepeatingQueryTransformer implements QueryTransformer {

    private final PromptRepetitionPolicy policy;

    public RepeatingQueryTransformer() {
        this(new PromptRepetitionPolicy());
    }

    public RepeatingQueryTransformer(PromptRepetitionPolicy policy) {
        this.policy = ensureNotNull(policy, "policy");
    }

    public PromptRepetitionDecision decide(Query query) {
        ensureNotNull(query, "query");
        return policy.decide(query.text());
    }

    @Override
    public Collection<Query> transform(Query query) {
        PromptRepetitionDecision decision = decide(query);
        Query transformed =
                query.metadata() == null ? Query.from(decision.text()) : Query.from(decision.text(), query.metadata());
        return singletonList(transformed);
    }

    public PromptRepetitionPolicy policy() {
        return policy;
    }
}
