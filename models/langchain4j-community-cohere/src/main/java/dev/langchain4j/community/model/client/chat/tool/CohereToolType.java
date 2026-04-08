package dev.langchain4j.community.model.client.chat.tool;

import static java.util.Locale.ROOT;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CohereToolType {
    FUNCTION;

    @JsonValue
    public String serialize() {
        return this.name().toLowerCase(ROOT);
    }
}
