package dev.langchain4j.community.store.embedding.yugabytedb;

import dev.langchain4j.internal.ValidationUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Metadata column definition parser for YugabyteDB embedding store.
 * <p>
 * Parses SQL column definitions and extracts key information for metadata storage.
 * Supports basic column types and common constraints.
 */
public class MetadataColumnDefinition {

    private final String fullDefinition;
    private final String name;
    private final String type;
    private final boolean nullable;

    private MetadataColumnDefinition(String fullDefinition, String name, String type, boolean nullable) {
        this.fullDefinition = fullDefinition;
        this.name = name;
        this.type = type;
        this.nullable = nullable;
    }

    /**
     * Parses a SQL column definition string into a MetadataColumnDefinition.
     * <p>
     * Examples:
     * <ul>
     * <li>{@code "user_id UUID NOT NULL"}</li>
     * <li>{@code "category TEXT"}</li>
     * <li>{@code "priority INTEGER NULL"}</li>
     * </ul>
     *
     * @param sqlDefinition SQL column definition string to parse
     * @return parsed MetadataColumnDefinition
     * @throws IllegalArgumentException if the definition format is invalid
     */
    public static MetadataColumnDefinition parse(String sqlDefinition) {
        String fullDefinition = ValidationUtils.ensureNotNull(sqlDefinition, "Metadata column definition");

        List<String> tokens = new ArrayList<>();
        for (String part : fullDefinition.trim().split("\\s+")) {
            tokens.add(part);
        }

        if (tokens.size() < 2) {
            throw new IllegalArgumentException("Column definition must have at least name and type. "
                    + "Format: column_name column_type [NULL|NOT NULL]. "
                    + "Example: 'user_id UUID NOT NULL'");
        }

        String name = tokens.get(0).toLowerCase();
        String type = tokens.get(1).toLowerCase();

        // Parse nullable constraint (default is nullable)
        boolean nullable = true;
        String definitionUpper = fullDefinition.toUpperCase();
        if (definitionUpper.contains("NOT NULL")) {
            nullable = false;
        }

        return new MetadataColumnDefinition(fullDefinition, name, type, nullable);
    }

    /**
     * Creates a simple column definition.
     *
     * @param name column name
     * @param type column type
     * @return MetadataColumnDefinition
     */
    public static MetadataColumnDefinition of(String name, String type) {
        return parse(name + " " + type);
    }

    /**
     * Creates a non-nullable column definition.
     *
     * @param name column name
     * @param type column type
     * @return MetadataColumnDefinition with NOT NULL constraint
     */
    public static MetadataColumnDefinition notNull(String name, String type) {
        return parse(name + " " + type + " NOT NULL");
    }

    public String getFullDefinition() {
        return fullDefinition;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public boolean isNullable() {
        return nullable;
    }

    @Override
    public String toString() {
        return String.format("MetadataColumnDefinition{name='%s', type='%s', nullable=%s}", name, type, nullable);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetadataColumnDefinition that = (MetadataColumnDefinition) o;
        return nullable == that.nullable && Objects.equals(name, that.name) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, nullable);
    }
}
