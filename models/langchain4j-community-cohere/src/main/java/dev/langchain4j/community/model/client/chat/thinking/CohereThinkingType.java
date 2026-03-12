package dev.langchain4j.community.model.client.chat.thinking;


import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum CohereThinkingType {

    ENABLED,
    DISABLED;

    @JsonValue
    public String serialize() { return name().toLowerCase(Locale.ROOT); }
}
