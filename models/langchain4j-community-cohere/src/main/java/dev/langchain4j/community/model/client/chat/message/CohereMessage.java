package dev.langchain4j.community.model.client.chat.message;

import com.fasterxml.jackson.annotation.JsonProperty;

public interface CohereMessage {

    @JsonProperty
    CohereRole role();
}
