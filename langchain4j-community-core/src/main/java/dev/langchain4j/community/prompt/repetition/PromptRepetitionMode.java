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
     * Always repeat when input is eligible.
     */
    ALWAYS
}
