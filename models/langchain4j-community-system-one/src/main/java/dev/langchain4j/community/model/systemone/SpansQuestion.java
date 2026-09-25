package dev.langchain4j.community.model.systemone;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.decision.Question;

/** djev's multiple grounded text-spans question. */
@Experimental
public record SpansQuestion(String instructions, Integer maxTokens, Integer maxItems) implements Question {
    public SpansQuestion {
        instructions = ensureNotBlank(instructions, "instructions");
        ensureTrue(maxTokens == null || maxTokens > 0, "maxTokens must be positive");
        ensureTrue(maxItems == null || maxItems > 0, "maxItems must be positive");
    }
}
