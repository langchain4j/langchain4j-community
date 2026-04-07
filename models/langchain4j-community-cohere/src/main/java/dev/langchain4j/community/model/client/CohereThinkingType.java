package dev.langchain4j.community.model.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 */
public enum CohereThinkingType {

    @JsonProperty("enabled")  ENABLED,

    @JsonProperty("disabled") DISABLED
}
