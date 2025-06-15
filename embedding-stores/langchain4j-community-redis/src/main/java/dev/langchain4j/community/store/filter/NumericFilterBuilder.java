package dev.langchain4j.community.store.filter;

import java.util.Objects;

/**
 * Builder for numeric field filters in Redis.
 */
public class NumericFilterBuilder {

    private final String fieldName;

    /**
     * Creates a new NumericFilterBuilder for the specified field.
     *
     * @param fieldName The name of the numeric field
     */
    NumericFilterBuilder(String fieldName) {
        this.fieldName = Objects.requireNonNull(fieldName, "Field name cannot be null");
    }

    /**
     * Creates an equality filter for numbers.
     *
     * @param value The numeric value to match
     * @return A filter expression matching the number
     */
    public FilterExpression equalTo(Number value) {
        if (value == null) {
            return new WildcardFilterExpression();
        }

        return new NumericFilterExpression(fieldName, value, value, false);
    }

    /**
     * Creates an inequality filter for numbers.
     *
     * @param value The numeric value to exclude
     * @return A filter expression excluding the number
     */
    public FilterExpression notEqualTo(Number value) {
        if (value == null) {
            return new WildcardFilterExpression();
        }

        return new NumericFilterExpression(fieldName, value, value, true);
    }

    /**
     * Creates a greater than filter for numbers.
     *
     * @param value The threshold value
     * @return A filter expression for values greater than the threshold
     */
    public FilterExpression greaterThan(Number value) {
        if (value == null) {
            return new WildcardFilterExpression();
        }

        return new NumericRangeFilterExpression("numeric_field", String.format("(%s", value), "+inf");
    }

    /**
     * Creates a greater than or equal filter for numbers.
     *
     * @param value The threshold value
     * @return A filter expression for values greater than or equal to the threshold
     */
    public FilterExpression greaterThanOrEqualTo(Number value) {
        if (value == null) {
            return new WildcardFilterExpression();
        }

        return new NumericRangeFilterExpression("numeric_field", value.toString(), "+inf");
    }

    /**
     * Creates a less than filter for numbers.
     *
     * @param value The threshold value
     * @return A filter expression for values less than the threshold
     */
    public FilterExpression lessThan(Number value) {
        if (value == null) {
            return new WildcardFilterExpression();
        }

        return new NumericRangeFilterExpression("numeric_field", "-inf", String.format("(%s", value));
    }

    /**
     * Creates a less than or equal filter for numbers.
     *
     * @param value The threshold value
     * @return A filter expression for values less than or equal to the threshold
     */
    public FilterExpression lessThanOrEqualTo(Number value) {
        if (value == null) {
            return new WildcardFilterExpression();
        }

        return new NumericRangeFilterExpression("numeric_field", "-inf", value.toString());
    }

    /**
     * Creates a filter for values between a range.
     *
     * @param start Range start value
     * @param end Range end value
     * @param rangeType Controls inclusivity of the range boundaries
     * @return A filter expression for values in the specified range
     */
    public FilterExpression between(Number start, Number end, RangeType rangeType) {
        if (start == null || end == null) {
            return new WildcardFilterExpression();
        }

        String startValue = start.toString();
        String endValue = end.toString();

        switch (rangeType) {
            case EXCLUSIVE:
                startValue = "(" + startValue;
                endValue = "(" + endValue;
                break;
            case LEFT_INCLUSIVE:
                endValue = "(" + endValue;
                break;
            case RIGHT_INCLUSIVE:
                startValue = "(" + startValue;
                break;
            case INCLUSIVE:
            default:
                // No modification needed, default is inclusive
                break;
        }

        return new NumericRangeFilterExpression(fieldName, startValue, endValue);
    }

    /**
     * Convenience method for between() with inclusive bounds.
     *
     * @param start Range start value
     * @param end Range end value
     * @return A filter expression for values in the specified range
     */
    public FilterExpression between(Number start, Number end) {
        return between(start, end, RangeType.INCLUSIVE);
    }

    /**
     * Numeric filter expression implementation.
     */
    private static class NumericFilterExpression extends AbstractFilterExpression {
        private final String fieldName;
        private final Number startValue;
        private final Number endValue;
        private final boolean negate;

        NumericFilterExpression(String fieldName, Number startValue, Number endValue, boolean negate) {
            this.fieldName = fieldName;
            this.startValue = startValue;
            this.endValue = endValue;
            this.negate = negate;
        }

        @Override
        public String toRedisQueryString() {
            String query = String.format("@%s:[%s %s]", fieldName, startValue, endValue);
            return negate ? String.format("(-%s)", query) : query;
        }
    }

    /**
     * Numeric range filter expression implementation.
     */
    private static class NumericRangeFilterExpression extends AbstractFilterExpression {
        private final String fieldName;
        private final String startValue;
        private final String endValue;

        NumericRangeFilterExpression(String fieldName, String startValue, String endValue) {
            this.fieldName = fieldName;
            this.startValue = startValue;
            this.endValue = endValue;
        }

        @Override
        public String toRedisQueryString() {
            return String.format("@%s:[%s %s]", fieldName, startValue, endValue);
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
