package dev.langchain4j.community.store.filter;

/**
 * Factory class for creating Redis-specific filters.
 */
public class RedisFilter {

    /**
     * Creates a filter for tag fields.
     *
     * @param fieldName The name of the tag field
     * @return A TagFilterBuilder for constructing tag filters
     */
    public static TagFilterBuilder tag(String fieldName) {
        return new TagFilterBuilder(fieldName);
    }

    /**
     * Creates a filter for numeric fields.
     *
     * @param fieldName The name of the numeric field
     * @return A NumericFilterBuilder for constructing numeric filters
     */
    public static NumericFilterBuilder numeric(String fieldName) {
        return new NumericFilterBuilder(fieldName);
    }

    /**
     * Creates a filter for text fields.
     *
     * @param fieldName The name of the text field
     * @return A TextFilterBuilder for constructing text filters
     */
    public static TextFilterBuilder text(String fieldName) {
        return new TextFilterBuilder(fieldName);
    }

    /**
     * Creates a filter for timestamp/date fields.
     *
     * @param fieldName The name of the timestamp field
     * @return A TimestampFilterBuilder for constructing timestamp filters
     */
    public static TimestampFilterBuilder timestamp(String fieldName) {
        return new TimestampFilterBuilder(fieldName);
    }

    /**
     * Creates a filter for geographic fields.
     *
     * @param fieldName The name of the geographic field
     * @return A GeoFilterBuilder for constructing geographic filters
     */
    public static GeoFilterBuilder geo(String fieldName) {
        return new GeoFilterBuilder(fieldName);
    }

    // Prevent instantiation
    private RedisFilter() {}
}
