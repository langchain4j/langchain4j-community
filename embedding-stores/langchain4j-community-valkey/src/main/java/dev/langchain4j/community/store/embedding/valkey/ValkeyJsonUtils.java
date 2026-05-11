package dev.langchain4j.community.store.embedding.valkey;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

class ValkeyJsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ValkeyJsonUtils() {
        throw new AssertionError("No instances");
    }

    static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new ValkeyRequestFailedException("Failed to serialize object to JSON", e);
        }
    }

    static Map<String, Object> toProperties(String jsonStr) {
        try {
            JavaType mapType = OBJECT_MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class);
            return OBJECT_MAPPER.readValue(jsonStr, mapType);
        } catch (JsonProcessingException e) {
            throw new ValkeyRequestFailedException("Failed to deserialize JSON to properties", e);
        }
    }
}
