package dev.langchain4j.community.prompt.repetition;

/**
 * Explains why repetition was applied or skipped.
 */
public enum PromptRepetitionReason {

    /**
     * Repetition was applied.
     */
    APPLIED,

    /**
     * Skipped because mode is {@link PromptRepetitionMode#NEVER}.
     */
    SKIPPED_MODE_NEVER,

    /**
     * Skipped because the input is not a supported single-text message.
     */
    SKIPPED_NON_TEXT,

    /**
     * Skipped because RAG augmentation has already happened.
     */
    SKIPPED_RAG_DETECTED,

    /**
     * Skipped because input length is over the configured limit.
     */
    SKIPPED_TOO_LONG,

    /**
     * Skipped because the text appears to request explicit reasoning.
     */
    SKIPPED_REASONING_INTENT,

    /**
     * Skipped because the input is already repeated with the configured separator.
     */
    SKIPPED_ALREADY_REPEATED
}
