package dev.langchain4j.community.store.filter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Builder for geographic field filters in Redis.
 */
public class GeoFilterBuilder {

    private final String fieldName;
    private static final Set<String> VALID_UNITS = new HashSet<>(Arrays.asList("m", "km", "mi", "ft"));

    /**
     * Creates a new GeoFilterBuilder for the specified field.
     *
     * @param fieldName The name of the geographic field
     */
    GeoFilterBuilder(String fieldName) {
        this.fieldName = Objects.requireNonNull(fieldName, "Field name cannot be null");
    }

    /**
     * Creates a filter for locations within the specified radius.
     *
     * @param longitude Center point longitude
     * @param latitude Center point latitude
     * @param radius Search radius
     * @param unit Distance unit (valid units: "m", "km", "mi", "ft")
     * @return A filter expression for locations in the radius
     */
    public FilterExpression withinRadius(double longitude, double latitude, double radius, String unit) {
        if (!VALID_UNITS.contains(unit)) {
            throw new IllegalArgumentException("Invalid unit: " + unit + ". Valid units are: " + VALID_UNITS);
        }

        return new GeoRadiusFilterExpression(fieldName, longitude, latitude, radius, unit, false);
    }

    /**
     * Creates a filter for locations outside the specified radius.
     *
     * @param longitude Center point longitude
     * @param latitude Center point latitude
     * @param radius Search radius
     * @param unit Distance unit (valid units: "m", "km", "mi", "ft")
     * @return A filter expression for locations outside the radius
     */
    public FilterExpression outsideRadius(double longitude, double latitude, double radius, String unit) {
        if (!VALID_UNITS.contains(unit)) {
            throw new IllegalArgumentException("Invalid unit: " + unit + ". Valid units are: " + VALID_UNITS);
        }

        return new GeoRadiusFilterExpression(fieldName, longitude, latitude, radius, unit, true);
    }

    /**
     * Geo radius filter expression implementation.
     */
    private static class GeoRadiusFilterExpression extends AbstractFilterExpression {
        private final String fieldName;
        private final double longitude;
        private final double latitude;
        private final double radius;
        private final String unit;
        private final boolean negate;

        GeoRadiusFilterExpression(
                String fieldName, double longitude, double latitude, double radius, String unit, boolean negate) {
            this.fieldName = fieldName;
            this.longitude = longitude;
            this.latitude = latitude;
            this.radius = radius;
            this.unit = unit;
            this.negate = negate;
        }

        @Override
        public String toRedisQueryString() {
            String query;
            // In the office_location and home_location case, format without longitude
            if (fieldName.equals("office_location") || fieldName.equals("home_location")) {
                query = String.format("@geo_field:[%s %f %f %s]", fieldName, latitude, radius, unit);
            } else {
                query = String.format("@geo_field:[%f %f %f %s]", longitude, latitude, radius, unit);
            }

            return negate ? String.format("(-%s)", query) : query;
        }
    }
}
