package dev.langchain4j.community.store.filter;

import java.util.Objects;

/**
 * Builder for text field filters in Redis.
 */
public class TextFilterBuilder {

    private final String fieldName;

    /**
     * Creates a new TextFilterBuilder for the specified field.
     *
     * @param fieldName The name of the text field
     */
    TextFilterBuilder(String fieldName) {
        this.fieldName = Objects.requireNonNull(fieldName, "Field name cannot be null");
    }

    /**
     * Creates an exact match filter for text.
     *
     * @param value The exact text to match
     * @return A filter expression for exact text match
     */
    public FilterExpression exactMatch(String value) {
        if (value == null || value.isEmpty()) {
            return new WildcardFilterExpression();
        }

        return new TextExactFilterExpression(fieldName, value, false);
    }

    /**
     * Creates a filter excluding exact text.
     *
     * @param value The exact text to exclude
     * @return A filter expression excluding the specified text
     */
    public FilterExpression notExactMatch(String value) {
        if (value == null || value.isEmpty()) {
            return new WildcardFilterExpression();
        }

        return new TextExactFilterExpression(fieldName, value, true);
    }

    /**
     * Creates a filter for text containing the specified string.
     *
     * @param value The substring to search for
     * @return A filter expression for text containing the substring
     */
    public FilterExpression contains(String value) {
        if (value == null || value.isEmpty()) {
            return new WildcardFilterExpression();
        }

        return new TextPatternFilterExpression(fieldName, value);
    }

    /**
     * Creates a filter for text matching the specified pattern.
     *
     * @param pattern The pattern to match (can use wildcards)
     * @return A filter expression for text matching the pattern
     */
    public FilterExpression matchesPattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return new WildcardFilterExpression();
        }

        return new TextPatternFilterExpression(fieldName, pattern);
    }

    /**
     * Creates a filter for fuzzy text matching.
     *
     * @param value The approximate text to match
     * @param fuzzyDistance The Levenshtein distance for fuzzy matching
     * @return A filter expression for fuzzy text matching
     */
    public FilterExpression fuzzyMatch(String value, int fuzzyDistance) {
        if (value == null || value.isEmpty()) {
            return new WildcardFilterExpression();
        }

        // Create fuzzy match pattern with % markers based on fuzzyDistance
        StringBuilder fuzzyPattern = new StringBuilder();

        // Add % characters based on the fuzzy distance (each % adds "fuzziness")
        for (int i = 0; i < fuzzyDistance; i++) {
            fuzzyPattern.append("%");
        }
        fuzzyPattern.append(value);
        for (int i = 0; i < fuzzyDistance; i++) {
            fuzzyPattern.append("%");
        }

        return new TextPatternFilterExpression(fieldName, fuzzyPattern.toString());
    }

    /**
     * Text exact match filter expression implementation.
     */
    private static class TextExactFilterExpression extends AbstractFilterExpression {
        private final String fieldName;
        private final String value;
        private final boolean negate;

        TextExactFilterExpression(String fieldName, String value, boolean negate) {
            this.fieldName = fieldName;
            this.value = value;
            this.negate = negate;
        }

        @Override
        public String toRedisQueryString() {
            if (negate) {
                return String.format("(-@%s:\"%s\")", fieldName, value);
            } else {
                return String.format("@%s:(\"%s\")", fieldName, value);
            }
        }
    }

    /**
     * Text pattern filter expression implementation.
     */
    private static class TextPatternFilterExpression extends AbstractFilterExpression {
        private final String fieldName;
        private final String pattern;

        TextPatternFilterExpression(String fieldName, String pattern) {
            this.fieldName = fieldName;
            this.pattern = pattern;
        }

        @Override
        public String toRedisQueryString() {
            return String.format("@%s:(%s)", fieldName, pattern);
        }
    }

    /**
     * Special filter expression that always returns "*", which matches everything in Redis.
     */
    private static class WildcardFilterExpression extends AbstractFilterExpression {
        @Override
        public String toRedisQueryString() {
            return "*";
        }
    }
}
