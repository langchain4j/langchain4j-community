package dev.langchain4j.community.model.client.chat.message.content;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CohereContentType {

    TEXT;

    @JsonValue
    public String serialize() { return name().toLowerCase(java.util.Locale.ROOT); }
}
