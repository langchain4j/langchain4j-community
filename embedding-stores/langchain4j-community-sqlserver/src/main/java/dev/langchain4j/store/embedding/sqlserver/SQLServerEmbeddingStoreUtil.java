package dev.langchain4j.store.embedding.sqlserver;

import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.store.embedding.sqlserver.exception.SQLServerLangChain4jException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import microsoft.sql.Vector;

/**
 * Utility methods for working with SQL Server embedding stores.
 */
class SQLServerEmbeddingStoreUtil {

    private static final ObjectMapper objectMapper =
            new ObjectMapper().configure(JsonWriteFeature.WRITE_NUMBERS_AS_STRINGS.mappedFeature(), true);

    /**
     * Converts an array of primitive float values to an array of Float objects.
     *
     * @param primitiveVector the input array of primitive float values
     * @return an array of Float objects corresponding to the input float array
     */
    public static Float[] boxEmbeddings(float[] primitiveVector) {
        Float[] boxedVector = new Float[primitiveVector.length];
        for (int i = 0; i < primitiveVector.length; i++) {
            boxedVector[i] = primitiveVector[i];
        }
        return boxedVector;
    }

    /**
     * Converts a given object representing a vector of Float objects into an array of primitive float values.
     * The method assumes that the input object is of type {@code Vector} containing an array of Float objects.
     * If the object is not of the expected type, an {@code IllegalArgumentException} is thrown.
     *
     * @param sourceVector the object representing the source vector, expected to be of type {@code Vector}
     *                     containing an array of Float objects
     * @return an array of primitive float values unboxed from the source vector
     * @throws IllegalArgumentException if the input object is not of the expected type or the vector contains invalid elements
     */
    public static float[] unboxEmbeddings(Object sourceVector) {
        if (!(sourceVector instanceof Vector vectorResult)) {
            throw new IllegalArgumentException("The source vector must be an array of Floats");
        }
        final Object[] data = vectorResult.getData();
        float[] unboxed = new float[data.length];
        for (int i = 0; i < data.length; i++) {
            if (data[i] == null || !(data[i] instanceof Float)) {
                // This should not happen
                unboxed[i] = 0.0f;
            } else {
                unboxed[i] = ((Float) data[i]).floatValue();
            }
        }
        return unboxed;
    }

    /**
     * Converts a {@link Metadata} object to its JSON string representation.
     *
     * @param metadata the {@link Metadata} object to be converted to JSON
     * @return a JSON string representing the provided metadata
     * @throws SQLServerLangChain4jException if an error occurs during serialization
     */
    public static String metadataToJson(Metadata metadata) {
        try {
            return objectMapper.writeValueAsString(metadata.toMap());
        } catch (IOException e) {
            throw new SQLServerLangChain4jException("Failed to serialize metadata to JSON", e);
        }
    }

    /**
     * Converts a JSON string representation into a {@link Metadata} object.
     * This method parses the provided JSON string and returns the corresponding Metadata object.
     * If the JSON string is null, it will return null. If deserialization fails, it throws a {@link SQLServerLangChain4jException}.
     *
     * @param json the JSON string to be converted to a {@link Metadata} object, may be null
     * @return a {@link Metadata} object representing the parsed JSON, or null if the input is null
     * @throws SQLServerLangChain4jException if an error occurs during deserialization
     */
    public static Metadata jsonToMetadata(String json) {
        if (json == null) {
            return null;
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            return Metadata.from(map);
        } catch (IOException e) {
            throw new SQLServerLangChain4jException("Failed to deserialize metadata from JSON", e);
        }
    }

    /**
     * Checks if a List contains a null element, and throws an exception if so.
     *
     * @param list List to check. Not null.
     * @param index Index of the list to check.
     * @param name Name of list, for use in an error message.
     * @return The list element. Not null.
     * @param <T> Class of the element
     * @throws IllegalArgumentException If the list element is null.
     */
    public static <T> T ensureIndexNotNull(List<T> list, int index, String name) {
        T value = list.get(index);

        if (value != null) return value;

        throw new IllegalArgumentException("null entry at index " + index + " in " + name);
    }

    /**
     * Checks if a given identifier contains potentially dangerous characters that could indicate SQL injection.
     */
    public static void checkSQLInjection(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return;
        }

        // Remove any existing square brackets and escape internal square brackets
        String cleaned = identifier.replace("[", "").replace("]", "");

        // Check for dangerous characters that could indicate SQL injection
        if (cleaned.contains(";")
                || cleaned.contains("--")
                || cleaned.contains("/*")
                || cleaned.toLowerCase().contains("drop ")
                || cleaned.toLowerCase().contains("delete ")
                || cleaned.toLowerCase().contains("insert ")
                || cleaned.toLowerCase().contains("update ")
                || cleaned.toLowerCase().contains("exec ")
                || cleaned.toLowerCase().contains("execute ")) {
            throw new IllegalArgumentException(
                    "SQL identifier contains potentially dangerous characters: " + identifier);
        }
    }
}
