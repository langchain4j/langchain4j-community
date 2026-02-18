package dev.langchain4j.community.model.client.chat.message;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum CohereRole {

    USER,
    ASSISTANT,
    SYSTEM;

    @JsonValue
    public String serialize() { return name().toLowerCase(Locale.ROOT); }
}
