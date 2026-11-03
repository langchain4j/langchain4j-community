package dev.langchain4j.community.web.search.deepseek;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;

final class DeepSeekJsonUtils {

    private static final Json.JsonCodec CODEC = ProviderJson.codec(ProviderJsonSpec.builder()
            .propertyNaming(ProviderJsonSpec.PropertyNaming.IDENTITY)
            .inclusion(ProviderJsonSpec.Inclusion.NON_NULL)
            .build());

    private DeepSeekJsonUtils() {}

    static String toJson(Object value) {
        return CODEC.toJson(value);
    }

    static <T> T fromJson(String value, Class<T> type) {
        return CODEC.fromJson(value, type);
    }
}
