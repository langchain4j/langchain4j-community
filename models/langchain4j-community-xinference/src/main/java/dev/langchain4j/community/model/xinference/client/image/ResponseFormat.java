package dev.langchain4j.community.model.xinference.client.image;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ResponseFormat {
    @JsonProperty("url")
    URL("url"),
    @JsonProperty("b64_json")
    B64_JSON("b64_json");

    final String value;

    ResponseFormat(final String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
