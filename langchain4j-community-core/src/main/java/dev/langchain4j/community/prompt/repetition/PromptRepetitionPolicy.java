package dev.langchain4j.community.prompt.repetition;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

/**
 * Applies deterministic prompt repetition with idempotence protection.
 * <p>
 * For PR1 this policy supports only NEVER/ALWAYS modes.
 */
public class PromptRepetitionPolicy {

    public static final PromptRepetitionMode DEFAULT_MODE = PromptRepetitionMode.ALWAYS;
    public static final String DEFAULT_SEPARATOR = "\n";

    private final PromptRepetitionMode mode;
    private final String separator;

    public PromptRepetitionPolicy() {
        this(DEFAULT_MODE, DEFAULT_SEPARATOR);
    }

    public PromptRepetitionPolicy(PromptRepetitionMode mode) {
        this(mode, DEFAULT_SEPARATOR);
    }

    public PromptRepetitionPolicy(PromptRepetitionMode mode, String separator) {
        this.mode = ensureNotNull(mode, "mode");
        ensureNotNull(separator, "separator");
        if (separator.isEmpty()) {
            throw new IllegalArgumentException("separator cannot be empty");
        }
        this.separator = separator;
    }

    /**
     * Decides whether repetition should be applied and returns the resulting text.
     */
    public PromptRepetitionDecision decide(String text) {
        ensureNotNull(text, "text");

        if (mode == PromptRepetitionMode.NEVER) {
            return PromptRepetitionDecision.skipped(PromptRepetitionReason.SKIPPED_MODE_NEVER, text);
        }

        if (isAlreadyRepeated(text)) {
            return PromptRepetitionDecision.skipped(PromptRepetitionReason.SKIPPED_ALREADY_REPEATED, text);
        }

        return PromptRepetitionDecision.applied(text, repeat(text));
    }

    private String repeat(String text) {
        return text + separator + text;
    }

    private boolean isAlreadyRepeated(String text) {
        int index = text.lastIndexOf(separator);
        if (index <= 0) {
            return false;
        }

        String left = text.substring(0, index);
        if (left.isBlank()) {
            return false;
        }

        String right = text.substring(index + separator.length());
        return left.equals(right);
    }

    public PromptRepetitionMode mode() {
        return mode;
    }

    public String separator() {
        return separator;
    }
}
