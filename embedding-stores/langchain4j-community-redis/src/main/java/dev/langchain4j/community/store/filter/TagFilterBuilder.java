package dev.langchain4j.community.store.filter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Builder for tag field filters in Redis.
 */
public class TagFilterBuilder {

    private final String fieldName;
    private final TokenEscaper escaper;

    /**
     * Creates a new TagFilterBuilder for the specified field.
     *
     * @param fieldName The name of the tag field
     */
    TagFilterBuilder(String fieldName) {
        this.fieldName = Objects.requireNonNull(fieldName, "Field name cannot be null");
        this.escaper = new TokenEscaper();
    }

    /**
     * Creates an equality filter for tags.
     *
     * @param value The tag value to match
     * @return A filter expression matching the tag
     */
    public FilterExpression equalTo(String value) {
        if (value == null || value.isEmpty()) {
            return new WildcardFilterExpression();
        }

        return new TagFilterExpression(fieldName, escaper.escape(value), false);
    }

    /**
     * Creates an inequality filter for tags.
     *
     * @param value The tag value to exclude
     * @return A filter expression excluding the tag
     */
    public FilterExpression notEqualTo(String value) {
        if (value == null || value.isEmpty()) {
            return new WildcardFilterExpression();
        }

        return new TagFilterExpression(fieldName, escaper.escape(value), true);
    }

    /**
     * Creates a filter matching tags in a collection.
     *
     * @param values Collection of values to match against
     * @return A filter expression matching any of the tags
     */
    public FilterExpression in(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return new WildcardFilterExpression();
        }

        // Filter out null or empty values and escape the rest
        String escapedValues = values.stream()
                .filter(v -> v != null && !v.isEmpty())
                .map(escaper::escape)
                .collect(Collectors.joining("|"));

        if (escapedValues.isEmpty()) {
            return new WildcardFilterExpression();
        }

        return new TagFilterExpression(fieldName, escapedValues, false);
    }

    /**
     * Creates a filter excluding tags in a collection.
     *
     * @param values Collection of values to exclude
     * @return A filter expression excluding all the tags
     */
    public FilterExpression notIn(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return new WildcardFilterExpression();
        }

        // Filter out null or empty values and escape the rest
        String escapedValues = values.stream()
                .filter(v -> v != null && !v.isEmpty())
                .map(escaper::escape)
                .collect(Collectors.joining("|"));

        if (escapedValues.isEmpty()) {
            return new WildcardFilterExpression();
        }

        return new TagFilterExpression(fieldName, escapedValues, true);
    }

    /**
     * Convenience method for in() with varargs.
     *
     * @param values Tag values to match
     * @return A filter expression matching any of the tags
     */
    public FilterExpression in(String... values) {
        return in(Arrays.asList(values));
    }

    /**
     * Convenience method for notIn() with varargs.
     *
     * @param values Tag values to exclude
     * @return A filter expression excluding all the tags
     */
    public FilterExpression notIn(String... values) {
        return notIn(Arrays.asList(values));
    }

    /**
     * Tag filter expression implementation.
     */
    private static class TagFilterExpression extends AbstractFilterExpression {
        private final String fieldName;
        private final String value;
        private final boolean negate;

        TagFilterExpression(String fieldName, String value, boolean negate) {
            this.fieldName = fieldName;
            this.value = value;
            this.negate = negate;
        }

        @Override
        public String toRedisQueryString() {
            String query = String.format("@%s:{%s}", fieldName, value);
            return negate ? String.format("(-%s)", query) : query;
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
