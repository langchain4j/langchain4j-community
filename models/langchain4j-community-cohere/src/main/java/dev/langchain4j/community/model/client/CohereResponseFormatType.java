package dev.langchain4j.community.model.client;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum CohereResponseFormatType {
    JSON_OBJECT;

    @JsonValue
    public String serialize() {
        return name().toLowerCase(Locale.ROOT);
    }
}
