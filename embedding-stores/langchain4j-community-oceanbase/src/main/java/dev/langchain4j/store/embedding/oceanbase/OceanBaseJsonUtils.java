package dev.langchain4j.store.embedding.oceanbase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

class OceanBaseJsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private OceanBaseJsonUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    static <T> T toObject(String jsonStr, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(jsonStr, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    static Map<String, Object> toMap(String jsonStr) {
        try {
            return OBJECT_MAPPER.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
