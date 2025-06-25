package dev.langchain4j.community.model.xinference.client.chat.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = ImageUrl.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class ImageUrl {
    private final String url;
    private final ImageDetail detail;

    private ImageUrl(Builder builder) {
        url = builder.url;
        detail = builder.detail;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ImageUrl of(String url, ImageDetail detail) {
        return builder().url(url).detail(detail).build();
    }

    public String getUrl() {
        return url;
    }

    public ImageDetail getDetail() {
        return detail;
    }

    public enum ImageDetail {
        @JsonProperty("low")
        LOW,
        @JsonProperty("high")
        HIGH,
        @JsonProperty("auto")
        AUTO
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String url;
        private ImageDetail detail;

        private Builder() {}

        public Builder url(String val) {
            url = val;
            return this;
        }

        public Builder detail(ImageDetail val) {
            detail = val;
            return this;
        }

        public ImageUrl build() {
            return new ImageUrl(this);
        }
    }
}
