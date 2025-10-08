package dev.langchain4j.community.store.embedding.yugabytedb;

/**
 * Enhanced filter mapper for COMBINED_JSON and COMBINED_JSONB metadata storage modes.
 * <p>
 * Converts filters to YugabyteDB-compatible SQL WHERE clauses using JSON/JSONB operators.
 * Supports PostgreSQL-style JSON path queries with proper type casting for YugabyteDB.
 * <p>
 * This mapper handles both JSON and JSONB columns efficiently, leveraging YugabyteDB's
 * PostgreSQL compatibility for optimal query performance.
 */
class JsonFilterMapper extends YugabyteDBFilterMapper {

    private final String metadataColumnName;

    /**
     * Creates a new JsonFilterMapper for the specified metadata column.
     *
     * @param metadataColumnName the name of the JSON/JSONB metadata column (e.g., "metadata")
     * @throws IllegalArgumentException if metadataColumnName is null or empty
     */
    public JsonFilterMapper(String metadataColumnName) {
        if (metadataColumnName == null || metadataColumnName.trim().isEmpty()) {
            throw new IllegalArgumentException("Metadata column name cannot be null or empty");
        }
        this.metadataColumnName = metadataColumnName.trim();
    }

    @Override
    String formatKey(String key, Class<?> valueType) {
        // Get SQL type, fallback to 'text' if not found
        String sqlType = SQL_TYPE_MAP.getOrDefault(valueType, "text");

        // Build JSON path query with type casting for YugabyteDB
        return String.format("(%s->>'%s')::%s", metadataColumnName, sanitizeJsonKey(key), sqlType);
    }

    @Override
    String formatKeyAsString(String key) {
        // Simple JSON text extraction without type casting
        return metadataColumnName + "->>" + formatJsonKey(key);
    }

    /**
     * Formats a JSON key with proper quoting for safe SQL inclusion.
     *
     * @param key the JSON key to format
     * @return properly quoted JSON key
     */
    private String formatJsonKey(String key) {
        return "'" + sanitizeJsonKey(key) + "'";
    }

    /**
     * Sanitizes JSON key to prevent injection and ensure valid JSON path.
     * Only allows alphanumeric characters, underscores, dots, and hyphens.
     *
     * @param key the JSON key to sanitize
     * @return sanitized key
     * @throws IllegalArgumentException if key contains invalid characters
     */
    private String sanitizeJsonKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("JSON key cannot be null or empty");
        }

        String cleanKey = key.trim();

        // Allow alphanumeric, underscore, dot, and hyphen for JSON keys
        if (!cleanKey.matches("^[a-zA-Z0-9_.\\-]+$")) {
            throw new IllegalArgumentException("Invalid JSON key '" + cleanKey + "'. Only alphanumeric characters, "
                    + "underscores, dots, and hyphens are allowed.");
        }

        return cleanKey;
    }

    /**
     * Gets the metadata column name used by this mapper.
     *
     * @return the metadata column name
     */
    public String getMetadataColumnName() {
        return metadataColumnName;
    }
}
