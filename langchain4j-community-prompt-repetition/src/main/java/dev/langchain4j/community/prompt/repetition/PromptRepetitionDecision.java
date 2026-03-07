package dev.langchain4j.community.prompt.repetition;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Objects;

/**
 * The result of a prompt repetition decision.
 */
public final class PromptRepetitionDecision {

    private final boolean applied;
    private final PromptRepetitionReason reason;
    private final String originalText;
    private final String text;
    private final int originalChars;
    private final int resultChars;

    private PromptRepetitionDecision(boolean applied, PromptRepetitionReason reason, String originalText, String text) {
        this.applied = applied;
        this.reason = ensureNotNull(reason, "reason");
        this.originalText = ensureNotNull(originalText, "originalText");
        this.text = ensureNotNull(text, "text");
        this.originalChars = originalText.length();
        this.resultChars = text.length();
    }

    public static PromptRepetitionDecision applied(String originalText, String text) {
        return new PromptRepetitionDecision(true, PromptRepetitionReason.APPLIED, originalText, text);
    }

    public static PromptRepetitionDecision skipped(PromptRepetitionReason reason, String text) {
        if (reason == PromptRepetitionReason.APPLIED) {
            throw new IllegalArgumentException("Use 'applied' factory method for APPLIED reason");
        }
        return new PromptRepetitionDecision(false, reason, text, text);
    }

    public boolean applied() {
        return applied;
    }

    public PromptRepetitionReason reason() {
        return reason;
    }

    /**
     * The original input text before transformation.
     */
    public String originalText() {
        return originalText;
    }

    /**
     * The text to use after applying this decision.
     */
    public String text() {
        return text;
    }

    public int originalChars() {
        return originalChars;
    }

    public int resultChars() {
        return resultChars;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PromptRepetitionDecision that)) return false;
        return applied == that.applied
                && originalChars == that.originalChars
                && resultChars == that.resultChars
                && reason == that.reason
                && Objects.equals(originalText, that.originalText)
                && Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(applied, reason, originalText, text, originalChars, resultChars);
    }

    @Override
    public String toString() {
        return "PromptRepetitionDecision{"
                + "applied="
                + applied
                + ", reason="
                + reason
                + ", originalChars="
                + originalChars
                + ", resultChars="
                + resultChars
                + '}';
    }
}
