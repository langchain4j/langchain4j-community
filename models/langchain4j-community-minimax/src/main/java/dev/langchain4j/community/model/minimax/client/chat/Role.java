package dev.langchain4j.community.model.minimax.client.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Role {
    @JsonProperty("system")
    SYSTEM,
    @JsonProperty("user")
    USER,
    @JsonProperty("assistant")
    ASSISTANT,
    @JsonProperty("tool")
    TOOL,
}
