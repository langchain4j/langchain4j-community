package dev.langchain4j.community.web.search.deepseek;

import com.fasterxml.jackson.databind.ObjectMapper;

final class DeepSeekJsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private DeepSeekJsonUtils() {}

    static String toJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize DeepSeek request", e);
        }
    }

    static <T> T fromJson(String value, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(value, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse DeepSeek response", e);
        }
    }
}
