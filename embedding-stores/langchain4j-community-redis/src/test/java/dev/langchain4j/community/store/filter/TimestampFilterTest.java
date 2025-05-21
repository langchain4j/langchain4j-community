package dev.langchain4j.community.store.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for the TimestampFilterBuilder and its expressions.
 */
public class TimestampFilterTest {

    @Test
    public void testOnDate() {
        LocalDate date = LocalDate.of(2023, 3, 17);
        FilterExpression filter = RedisFilter.timestamp("created_at").onDate(date);

        // Expected timestamps: start of day to end of day
        long startEpoch = date.atStartOfDay().toEpochSecond(ZoneOffset.UTC);
        long endEpoch = date.plusDays(1).atStartOfDay().minusNanos(1).toEpochSecond(ZoneOffset.UTC);

        assertEquals(String.format("@created_at:[%d %d]", startEpoch, endEpoch), filter.toRedisQueryString());

        // Test with null
        assertEquals("*", RedisFilter.timestamp("created_at").onDate(null).toRedisQueryString());
    }

    @Test
    public void testAt() {
        LocalDateTime dateTime = LocalDateTime.of(2023, 3, 17, 14, 30, 0);
        FilterExpression filter = RedisFilter.timestamp("created_at").at(dateTime);

        long epoch = dateTime.toEpochSecond(ZoneOffset.UTC);

        assertEquals(String.format("@created_at:[%d %d]", epoch, epoch), filter.toRedisQueryString());

        // Test with null
        assertEquals("*", RedisFilter.timestamp("created_at").at(null).toRedisQueryString());
    }

    @Test
    public void testBefore() {
        LocalDateTime dateTime = LocalDateTime.of(2023, 3, 17, 14, 30, 0);
        FilterExpression filter = RedisFilter.timestamp("created_at").before(dateTime);

        long epoch = dateTime.toEpochSecond(ZoneOffset.UTC);

        assertEquals(String.format("@created_at:[-inf (%d]", epoch), filter.toRedisQueryString());

        // Test with null
        assertEquals("*", RedisFilter.timestamp("created_at").before(null).toRedisQueryString());
    }

    @Test
    public void testAfter() {
        LocalDateTime dateTime = LocalDateTime.of(2023, 3, 17, 14, 30, 0);
        FilterExpression filter = RedisFilter.timestamp("created_at").after(dateTime);

        long epoch = dateTime.toEpochSecond(ZoneOffset.UTC);

        assertEquals(String.format("@created_at:[(%d +inf]", epoch), filter.toRedisQueryString());

        // Test with null
        assertEquals("*", RedisFilter.timestamp("created_at").after(null).toRedisQueryString());
    }

    @ParameterizedTest
    @MethodSource("betweenTestData")
    public void testBetween(LocalDateTime start, LocalDateTime end, RangeType rangeType, String expected) {

        FilterExpression filter;

        if (rangeType != null) {
            filter = RedisFilter.timestamp("created_at").between(start, end, rangeType);
        } else {
            filter = RedisFilter.timestamp("created_at").between(start, end);
        }

        assertEquals(expected, filter.toRedisQueryString());
    }

    static Stream<Arguments> betweenTestData() {
        LocalDateTime start = LocalDateTime.of(2023, 3, 1, 0, 0, 0);
        LocalDateTime end = LocalDateTime.of(2023, 3, 31, 23, 59, 59);

        long startEpoch = start.toEpochSecond(ZoneOffset.UTC);
        long endEpoch = end.toEpochSecond(ZoneOffset.UTC);

        return Stream.of(
                // Default (inclusive) between
                Arguments.of(start, end, null, String.format("@created_at:[%d %d]", startEpoch, endEpoch)),

                // Between with explicit range types
                Arguments.of(
                        start, end, RangeType.INCLUSIVE, String.format("@created_at:[%d %d]", startEpoch, endEpoch)),
                Arguments.of(
                        start, end, RangeType.EXCLUSIVE, String.format("@created_at:[(%d (%d]", startEpoch, endEpoch)),
                Arguments.of(
                        start,
                        end,
                        RangeType.LEFT_INCLUSIVE,
                        String.format("@created_at:[%d (%d]", startEpoch, endEpoch)),
                Arguments.of(
                        start,
                        end,
                        RangeType.RIGHT_INCLUSIVE,
                        String.format("@created_at:[(%d %d]", startEpoch, endEpoch)),

                // Edge cases
                Arguments.of(null, end, null, "*"),
                Arguments.of(start, null, null, "*"),
                Arguments.of(null, null, null, "*"));
    }

    @Test
    public void testComplexTimestampFilters() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime weekAgo = now.minusDays(7);
        LocalDateTime monthAgo = now.minusMonths(1);
        LocalDate today = LocalDate.now();

        // Between timestamps
        FilterExpression filter = RedisFilter.timestamp("created_at").between(weekAgo, now);

        // Time range with day precision
        filter = RedisFilter.timestamp("created_at")
                .onDate(today)
                .and(RedisFilter.numeric("views").greaterThan(100));

        // Combine with other filters
        filter = RedisFilter.timestamp("created_at")
                .after(monthAgo)
                .and(RedisFilter.tag("status").equalTo("active"))
                .and(RedisFilter.text("content").contains("important"));

        // Recent or popular
        filter = RedisFilter.timestamp("created_at")
                .after(weekAgo)
                .or(RedisFilter.numeric("popularity").greaterThanOrEqualTo(4.5));

        // Exclusive time ranges
        filter = RedisFilter.timestamp("created_at")
                .after(weekAgo)
                .and(RedisFilter.timestamp("created_at").before(now));

        // This is equivalent to between, just testing the combination
        long weekAgoEpoch = weekAgo.toEpochSecond(ZoneOffset.UTC);
        long nowEpoch = now.toEpochSecond(ZoneOffset.UTC);

        assertEquals(
                String.format("(@created_at:[(%d +inf] @created_at:[-inf (%d])", weekAgoEpoch, nowEpoch),
                filter.toRedisQueryString());
    }
}
