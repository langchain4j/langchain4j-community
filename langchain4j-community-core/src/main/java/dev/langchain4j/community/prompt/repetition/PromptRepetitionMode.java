package dev.langchain4j.community.prompt.repetition;

/**
 * Controls when prompt repetition should be applied.
 */
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
