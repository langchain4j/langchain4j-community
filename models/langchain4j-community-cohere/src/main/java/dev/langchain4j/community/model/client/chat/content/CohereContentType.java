package dev.langchain4j.community.model.client.chat.content;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CohereContentType {

    TEXT,
    IMAGE_URL,
    THINKING;

    @JsonValue
    public String serialize() { return name().toLowerCase(java.util.Locale.ROOT); }
}
