package dev.langchain4j.community.store.embedding.yugabytedb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Metadata;
import java.util.Map;

/**
 * JSON utility class for YugabyteDB operations
 *
 * Provides serialization and deserialization utilities for handling metadata
 * and other JSON operations in YugabyteDB embedding storage.
 */
public class YugabyteDBJsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private YugabyteDBJsonUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    /**
     * Convert object to JSON string
     *
     * @param object the object to serialize
     * @return JSON string representation
     * @throws YugabyteDBRequestFailedException if serialization fails
     */
    public static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new YugabyteDBRequestFailedException("Failed to serialize object to JSON", e);
        }
    }

    /**
     * Convert JSON string to object of specified class
     *
     * @param jsonStr the JSON string
     * @param clazz the target class
     * @param <T> the type parameter
     * @return deserialized object
     * @throws YugabyteDBRequestFailedException if deserialization fails
     */
    static <T> T toObject(String jsonStr, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(jsonStr, clazz);
        } catch (JsonProcessingException e) {
            throw new YugabyteDBRequestFailedException("Failed to deserialize JSON to object", e);
        }
    }

    /**
     * Convert JSON string to object using TypeReference
     *
     * @param jsonStr the JSON string
     * @param typeReference the type reference
     * @param <T> the type parameter
     * @return deserialized object
     * @throws YugabyteDBRequestFailedException if deserialization fails
     */
    static <T> T toObject(String jsonStr, TypeReference<T> typeReference) {
        try {
            return OBJECT_MAPPER.readValue(jsonStr, typeReference);
        } catch (JsonProcessingException e) {
            throw new YugabyteDBRequestFailedException("Failed to deserialize JSON to object", e);
        }
    }

    /**
     * Convert JSON string to properties map
     *
     * @param jsonStr the JSON string
     * @return map of properties
     * @throws YugabyteDBRequestFailedException if deserialization fails
     */
    static Map<String, Object> toProperties(String jsonStr) {
        try {
            JavaType mapType = OBJECT_MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
            return OBJECT_MAPPER.readValue(jsonStr, mapType);
        } catch (JsonProcessingException e) {
            throw new YugabyteDBRequestFailedException("Failed to deserialize JSON to properties", e);
        }
    }

    /**
     * Serialize Metadata to JSON string
     *
     * @param metadata the metadata to serialize
     * @return JSON string representation, or null if metadata is null
     * @throws YugabyteDBRequestFailedException if serialization fails
     */
    public static String serializeMetadata(Metadata metadata) {
        if (metadata == null) {
            return null;
        }
        return toJson(metadata.toMap());
    }

    /**
     * Deserialize JSON string to Metadata
     *
     * @param metadataJson the JSON string
     * @return Metadata object, or empty Metadata if input is null/blank
     * @throws YugabyteDBRequestFailedException if deserialization fails
     */
    public static Metadata deserializeMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.trim().isEmpty()) {
            return new Metadata();
        }

        try {
            Map<String, Object> map = toProperties(metadataJson);
            return new Metadata(map);
        } catch (Exception e) {
            // Log warning and return empty metadata instead of failing
            // This provides more resilient behavior for corrupted metadata
            return new Metadata();
        }
    }
}
