package dev.langchain4j.community.store.filter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Builder for timestamp/date field filters in Redis.
 */
public class TimestampFilterBuilder {

    private final String fieldName;

    /**
     * Creates a new TimestampFilterBuilder for the specified field.
     *
     * @param fieldName The name of the timestamp field
     */
    TimestampFilterBuilder(String fieldName) {
        this.fieldName = Objects.requireNonNull(fieldName, "Field name cannot be null");
    }

    /**
     * Creates a filter for timestamps equal to the specified date.
     * For just a date, this covers the entire day.
     *
     * @param date The date to match
     * @return A filter expression for the specified date
     */
    public FilterExpression onDate(LocalDate date) {
        if (date == null) {
            return new WildcardFilterExpression();
        }

        // For a date, create a range covering the whole day
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.plusDays(1).atStartOfDay().minusNanos(1);

        return between(start, end);
    }

    /**
     * Creates a filter for timestamps equal to the specified date and time.
     *
     * @param dateTime The exact date and time to match
     * @return A filter expression for the exact timestamp
     */
    public FilterExpression at(LocalDateTime dateTime) {
        if (dateTime == null) {
            return new WildcardFilterExpression();
        }

        long epochSeconds = dateTime.toEpochSecond(ZoneOffset.UTC);

        return new TimestampEqualFilterExpression(fieldName, epochSeconds, false);
    }

    /**
     * Creates a filter for timestamps before the specified date and time.
     *
     * @param dateTime The cutoff date and time
     * @return A filter expression for timestamps before the cutoff
     */
    public FilterExpression before(LocalDateTime dateTime) {
        if (dateTime == null) {
            return new WildcardFilterExpression();
        }

        long epochSeconds = dateTime.toEpochSecond(ZoneOffset.UTC);

        return new TimestampRangeFilterExpression(fieldName, "-inf", "(" + epochSeconds);
    }

    /**
     * Creates a filter for timestamps after the specified date and time.
     *
     * @param dateTime The cutoff date and time
     * @return A filter expression for timestamps after the cutoff
     */
    public FilterExpression after(LocalDateTime dateTime) {
        if (dateTime == null) {
            return new WildcardFilterExpression();
        }

        long epochSeconds = dateTime.toEpochSecond(ZoneOffset.UTC);

        return new TimestampRangeFilterExpression(fieldName, "(" + epochSeconds, "+inf");
    }

    /**
     * Creates a filter for timestamps between the specified date range.
     *
     * @param start Range start date/time
     * @param end Range end date/time
     * @param rangeType Controls inclusivity of the range boundaries
     * @return A filter expression for timestamps in the specified range
     */
    public FilterExpression between(LocalDateTime start, LocalDateTime end, RangeType rangeType) {
        if (start == null || end == null) {
            return new WildcardFilterExpression();
        }

        long startEpoch = start.toEpochSecond(ZoneOffset.UTC);
        long endEpoch = end.toEpochSecond(ZoneOffset.UTC);

        String startValue = String.valueOf(startEpoch);
        String endValue = String.valueOf(endEpoch);

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

        return new TimestampRangeFilterExpression(fieldName, startValue, endValue);
    }

    /**
     * Convenience method for between() with inclusive bounds.
     *
     * @param start Range start date/time
     * @param end Range end date/time
     * @return A filter expression for timestamps in the specified range
     */
    public FilterExpression between(LocalDateTime start, LocalDateTime end) {
        return between(start, end, RangeType.INCLUSIVE);
    }

    /**
     * Timestamp equal filter expression implementation.
     */
    private static class TimestampEqualFilterExpression extends AbstractFilterExpression {
        private final String fieldName;
        private final long epochSeconds;
        private final boolean negate;

        TimestampEqualFilterExpression(String fieldName, long epochSeconds, boolean negate) {
            this.fieldName = fieldName;
            this.epochSeconds = epochSeconds;
            this.negate = negate;
        }

        @Override
        public String toRedisQueryString() {
            String query = String.format("@%s:[%d %d]", fieldName, epochSeconds, epochSeconds);
            return negate ? String.format("(-%s)", query) : query;
        }
    }

    /**
     * Timestamp range filter expression implementation.
     */
    private static class TimestampRangeFilterExpression extends AbstractFilterExpression {
        private final String fieldName;
        private final String startValue;
        private final String endValue;

        TimestampRangeFilterExpression(String fieldName, String startValue, String endValue) {
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
