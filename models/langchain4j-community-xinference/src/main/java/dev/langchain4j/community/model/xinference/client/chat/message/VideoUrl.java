package dev.langchain4j.community.model.xinference.client.chat.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = VideoUrl.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class VideoUrl {
    private final String url;

    private VideoUrl(Builder builder) {
        url = builder.url;
    }

    public String getUrl() {
        return url;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static VideoUrl of(String url) {
        return builder().url(url).build();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String url;

        private Builder() {}

        public Builder url(String val) {
            url = val;
            return this;
        }

        public VideoUrl build() {
            return new VideoUrl(this);
        }
    }
}
