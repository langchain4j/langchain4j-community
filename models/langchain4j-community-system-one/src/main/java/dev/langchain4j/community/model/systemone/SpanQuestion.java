package dev.langchain4j.community.model.systemone;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureTrue;

import dev.langchain4j.Experimental;
import dev.langchain4j.model.structureddecision.Question;

/** djev's single grounded text-span question. */
@Experimental
public record SpanQuestion(String instructions, Integer maxTokens) implements Question {
    public SpanQuestion {
        instructions = ensureNotBlank(instructions, "instructions");
        ensureTrue(maxTokens == null || maxTokens > 0, "maxTokens must be positive");
    }
}
