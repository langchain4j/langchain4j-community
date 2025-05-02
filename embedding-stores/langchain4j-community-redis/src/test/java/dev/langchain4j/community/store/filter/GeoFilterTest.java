package dev.langchain4j.community.store.filter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for the GeoFilterBuilder and its expressions.
 */
public class GeoFilterTest {

    @ParameterizedTest
    @MethodSource("withinRadiusTestData")
    public void testWithinRadius(double longitude, double latitude, double radius, String unit, String expected) {
        FilterExpression filter = RedisFilter.geo("geo_field").withinRadius(longitude, latitude, radius, unit);
        assertEquals(expected, filter.toRedisQueryString());
    }

    static Stream<Arguments> withinRadiusTestData() {
        return Stream.of(
                Arguments.of(1.0, 2.0, 3.0, "km", "@geo_field:[1.000000 2.000000 3.000000 km]"),
                Arguments.of(-122.4194, 37.7749, 5.0, "km", "@geo_field:[-122.419400 37.774900 5.000000 km]"),
                Arguments.of(0.0, 0.0, 1000.0, "m", "@geo_field:[0.000000 0.000000 1000.000000 m]"),
                Arguments.of(-74.0060, 40.7128, 10.0, "mi", "@geo_field:[-74.006000 40.712800 10.000000 mi]"),
                Arguments.of(139.6917, 35.6895, 500.0, "ft", "@geo_field:[139.691700 35.689500 500.000000 ft]"));
    }

    @ParameterizedTest
    @MethodSource("outsideRadiusTestData")
    public void testOutsideRadius(double longitude, double latitude, double radius, String unit, String expected) {
        FilterExpression filter = RedisFilter.geo("geo_field").outsideRadius(longitude, latitude, radius, unit);
        assertEquals(expected, filter.toRedisQueryString());
    }

    static Stream<Arguments> outsideRadiusTestData() {
        return Stream.of(
                Arguments.of(1.0, 2.0, 3.0, "km", "(-@geo_field:[1.000000 2.000000 3.000000 km])"),
                Arguments.of(-122.4194, 37.7749, 5.0, "km", "(-@geo_field:[-122.419400 37.774900 5.000000 km])"),
                Arguments.of(0.0, 0.0, 1000.0, "m", "(-@geo_field:[0.000000 0.000000 1000.000000 m])"),
                Arguments.of(-74.0060, 40.7128, 10.0, "mi", "(-@geo_field:[-74.006000 40.712800 10.000000 mi])"),
                Arguments.of(139.6917, 35.6895, 500.0, "ft", "(-@geo_field:[139.691700 35.689500 500.000000 ft])"));
    }

    @Test
    public void testInvalidUnit() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            RedisFilter.geo("geo_field").withinRadius(1.0, 2.0, 3.0, "invalid_unit");
        });

        assertTrue(exception.getMessage().contains("Invalid unit"));

        exception = assertThrows(IllegalArgumentException.class, () -> {
            RedisFilter.geo("geo_field").outsideRadius(1.0, 2.0, 3.0, "invalid_unit");
        });

        assertTrue(exception.getMessage().contains("Invalid unit"));
    }

    @Test
    public void testComplexGeoFilters() {
        // Geo filter with other geo filter
        FilterExpression filter = RedisFilter.geo("office_location")
                .withinRadius(-122.4194, 37.7749, 5.0, "km")
                .and(RedisFilter.geo("home_location").withinRadius(-74.0060, 40.7128, 10.0, "mi"));

        assertEquals(
                "(@geo_field:[office_location 37.774900 5.000000 km] @geo_field:[home_location 40.712800 10.000000 mi])",
                filter.toRedisQueryString());

        // Geo filter with tag filter
        filter = RedisFilter.geo("location")
                .withinRadius(139.6917, 35.6895, 5.0, "km")
                .and(RedisFilter.tag("city").equalTo("Tokyo"));

        String geoTagFilter = filter.toRedisQueryString();
        assertTrue(geoTagFilter.contains("@geo_field:[139.691700 35.689500 5.000000 km]"));
        assertTrue(geoTagFilter.contains("@city:{Tokyo}"));

        // Geo filter with text filter
        filter = RedisFilter.geo("location")
                .withinRadius(139.6917, 35.6895, 5.0, "km")
                .and(RedisFilter.text("description").contains("restaurant"));

        String geoTextFilter = filter.toRedisQueryString();
        assertTrue(geoTextFilter.contains("@geo_field:[139.691700 35.689500 5.000000 km]"));
        assertTrue(geoTextFilter.contains("@description:(restaurant)"));

        // Geo filter with numeric filter
        filter = RedisFilter.geo("location")
                .withinRadius(139.6917, 35.6895, 5.0, "km")
                .and(RedisFilter.numeric("numeric_field").greaterThanOrEqualTo(4.0));

        String geoNumericFilter = filter.toRedisQueryString();
        assertTrue(geoNumericFilter.contains("@geo_field:[139.691700 35.689500 5.000000 km]"));
        assertTrue(geoNumericFilter.contains("@numeric_field:[4.0 +inf]"));

        // Inside one area or another
        filter = RedisFilter.geo("location")
                .withinRadius(-122.4194, 37.7749, 5.0, "km")
                .or(RedisFilter.geo("location").withinRadius(-74.0060, 40.7128, 5.0, "km"));

        String orGeoFilter = filter.toRedisQueryString();
        assertTrue(orGeoFilter.contains("@geo_field:[-122.419400 37.774900 5.000000 km]"));
        assertTrue(orGeoFilter.contains("@geo_field:[-74.006000 40.712800 5.000000 km]"));
        assertTrue(orGeoFilter.contains("|")); // Check for OR operator

        // Inside one area but not another
        filter = RedisFilter.geo("location")
                .withinRadius(-122.4194, 37.7749, 10.0, "km")
                .and(RedisFilter.geo("location").outsideRadius(-122.4194, 37.7749, 2.0, "km"));

        String complexGeoFilter = filter.toRedisQueryString();
        assertTrue(complexGeoFilter.contains("@geo_field:[-122.419400 37.774900 10.000000 km]"));
        assertTrue(complexGeoFilter.contains("(-@geo_field:[-122.419400 37.774900 2.000000 km])"));
    }
}
