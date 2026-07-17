package dev.langchain4j.community.prompt.repetition;

import dev.langchain4j.Experimental;

/**
 * Controls when prompt repetition should be applied.
 */
@Experimental
public enum PromptRepetitionMode {

    /**
     * Never repeat.
     */
    NEVER,

    /**
     * Apply stop-loss gates first, then repeat only when eligible.
     */
    AUTO,

    /**
     * Always repeat when input is eligible.
     */
    ALWAYS
}
