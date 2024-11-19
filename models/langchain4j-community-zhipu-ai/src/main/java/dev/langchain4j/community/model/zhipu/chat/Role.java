package dev.langchain4j.community.model.zhipu.chat;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum Role {
    SYSTEM,
    USER,
    ASSISTANT,
    FUNCTION,
    TOOL;

    @JsonValue
    public String serialize() {
        return name().toLowerCase(Locale.ROOT);
    }
}
