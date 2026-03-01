package dev.langchain4j.community.model.client.chat.response;


import com.fasterxml.jackson.annotation.JsonValue;

import static java.util.Locale.ROOT;

public enum CohereToolType {

    FUNCTION;

    @JsonValue
    public String serialize() { return this.name().toLowerCase(ROOT); }
}
