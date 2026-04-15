package dev.langchain4j.community.model.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cohere thinking mode for reasoning models.
 * <br/>
 * More details are available <a href="https://docs.cohere.com/reference/chat-stream#request.body.thinking.type">here</a>.
 */
public enum CohereThinkingType {
    @JsonProperty("enabled")
    ENABLED,

    @JsonProperty("disabled")
    DISABLED
}
