package dev.langchain4j.community.model.xinference.client.chat.message;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ContentType {
    @JsonProperty("text")
    TEXT,
    @JsonProperty("image_url")
    IMAGE_URL,
    @JsonProperty("video_url")
    VIDEO_URL,
    @JsonProperty("audio")
    AUDIO
}
