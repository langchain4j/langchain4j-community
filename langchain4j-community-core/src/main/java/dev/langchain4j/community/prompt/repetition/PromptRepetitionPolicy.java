package dev.langchain4j.community.prompt.repetition;

import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Applies deterministic prompt repetition with idempotence protection.
 * <p>
 * In {@link PromptRepetitionMode#AUTO}, repetition is skipped for very long inputs and prompts
 * that appear to request explicit reasoning steps.
 */
public class PromptRepetitionPolicy {

    public static final PromptRepetitionMode DEFAULT_MODE = PromptRepetitionMode.AUTO;
    public static final String DEFAULT_SEPARATOR = "\n";
    public static final int DEFAULT_MAX_CHARS = 8_000;
    public static final Set<String> DEFAULT_REASONING_KEYWORDS = Set.of(
            "step by step", "think step by step", "chain of thought", "show your reasoning", "reasoning process");

    private final PromptRepetitionMode mode;
    private final String separator;
    private final int maxChars;
    private final Set<String> reasoningKeywords;

    public PromptRepetitionPolicy() {
        this(DEFAULT_MODE, DEFAULT_SEPARATOR, DEFAULT_MAX_CHARS, DEFAULT_REASONING_KEYWORDS);
    }

    public PromptRepetitionPolicy(PromptRepetitionMode mode) {
        this(mode, DEFAULT_SEPARATOR, DEFAULT_MAX_CHARS, DEFAULT_REASONING_KEYWORDS);
    }

    public PromptRepetitionPolicy(PromptRepetitionMode mode, String separator) {
        this(mode, separator, DEFAULT_MAX_CHARS, DEFAULT_REASONING_KEYWORDS);
    }

    public PromptRepetitionPolicy(
            PromptRepetitionMode mode, String separator, int maxChars, Collection<String> reasoningKeywords) {
        this.mode = ensureNotNull(mode, "mode");
        ensureNotNull(separator, "separator");
        if (separator.isEmpty()) {
            throw new IllegalArgumentException("separator cannot be empty");
        }
        this.separator = separator;
        this.maxChars = ensureGreaterThanZero(maxChars, "maxChars");
        this.reasoningKeywords = normalizeReasoningKeywords(reasoningKeywords);
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

        if (mode == PromptRepetitionMode.AUTO) {
            if (text.length() > maxChars) {
                return PromptRepetitionDecision.skipped(PromptRepetitionReason.SKIPPED_TOO_LONG, text);
            }
            if (hasReasoningIntent(text)) {
                return PromptRepetitionDecision.skipped(PromptRepetitionReason.SKIPPED_REASONING_INTENT, text);
            }
        }

        return PromptRepetitionDecision.applied(text, repeat(text));
    }

    private String repeat(String text) {
        return text + separator + text;
    }

    private boolean hasReasoningIntent(String text) {
        if (reasoningKeywords.isEmpty()) {
            return false;
        }

        String normalizedText = text.toLowerCase(Locale.ROOT);
        for (String keyword : reasoningKeywords) {
            if (normalizedText.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAlreadyRepeated(String text) {
        int index = text.indexOf(separator);
        while (index > 0) {
            String left = text.substring(0, index);
            if (!left.isBlank()) {
                String right = text.substring(index + separator.length());
                if (left.equals(right)) {
                    return true;
                }
            }
            index = text.indexOf(separator, index + separator.length());
        }
        return false;
    }

    public PromptRepetitionMode mode() {
        return mode;
    }

    public String separator() {
        return separator;
    }

    public int maxChars() {
        return maxChars;
    }

    public Set<String> reasoningKeywords() {
        return reasoningKeywords;
    }

    public static Builder builder() {
        return new Builder();
    }

    private static Set<String> normalizeReasoningKeywords(Collection<String> keywords) {
        ensureNotNull(keywords, "reasoningKeywords");

        Set<String> normalized = new LinkedHashSet<>();
        for (String keyword : keywords) {
            if (keyword == null) {
                continue;
            }
            String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
            if (!normalizedKeyword.isEmpty()) {
                normalized.add(normalizedKeyword);
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    public static class Builder {

        private PromptRepetitionMode mode = DEFAULT_MODE;
        private String separator = DEFAULT_SEPARATOR;
        private int maxChars = DEFAULT_MAX_CHARS;
        private Collection<String> reasoningKeywords = DEFAULT_REASONING_KEYWORDS;

        Builder() {}

        public Builder mode(PromptRepetitionMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder separator(String separator) {
            this.separator = separator;
            return this;
        }

        public Builder maxChars(int maxChars) {
            this.maxChars = maxChars;
            return this;
        }

        public Builder reasoningKeywords(Collection<String> reasoningKeywords) {
            this.reasoningKeywords = reasoningKeywords;
            return this;
        }

        public PromptRepetitionPolicy build() {
            return new PromptRepetitionPolicy(mode, separator, maxChars, reasoningKeywords);
        }
    }
}
