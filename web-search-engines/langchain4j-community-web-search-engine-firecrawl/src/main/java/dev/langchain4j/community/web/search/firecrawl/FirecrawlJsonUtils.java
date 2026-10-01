package dev.langchain4j.community.web.search.firecrawl;

import dev.langchain4j.internal.Json;
import dev.langchain4j.internal.ProviderJson;
import dev.langchain4j.internal.ProviderJsonSpec;

class FirecrawlJsonUtils {

    private FirecrawlJsonUtils() throws InstantiationException {
        throw new InstantiationException("Can't instantiate this utility class.");
    }

    private static final Json.JsonCodec CODEC = ProviderJson.codec(ProviderJsonSpec.builder()
            .propertyNaming(ProviderJsonSpec.PropertyNaming.IDENTITY)
            .inclusion(ProviderJsonSpec.Inclusion.NON_NULL)
            .build());

    static String toJson(Object object) {
        return CODEC.toJson(object);
    }

    static <T> T fromJson(String jsonStr, Class<T> clazz) {
        return CODEC.fromJson(jsonStr, clazz);
    }
}
