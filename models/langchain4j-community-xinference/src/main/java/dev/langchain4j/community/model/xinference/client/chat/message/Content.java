package dev.langchain4j.community.model.xinference.client.chat.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = Content.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class Content {
    private final ContentType type;
    private final String text;
    private final ImageUrl imageUrl;
    private final VideoUrl videoUrl;

    private Content(Builder builder) {
        type = builder.type;
        text = builder.text;
        imageUrl = builder.imageUrl;
        videoUrl = builder.videoUrl;
    }

    public static Content text(String text) {
        return builder().type(ContentType.TEXT).text(text).build();
    }

    public static Content image(ImageUrl imageUrl) {
        return builder().type(ContentType.IMAGE_URL).imageUrl(imageUrl).build();
    }

    public static Content video(VideoUrl videoUrl) {
        return builder().type(ContentType.VIDEO_URL).videoUrl(videoUrl).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public ContentType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public ImageUrl getImageUrl() {
        return imageUrl;
    }

    public VideoUrl getVideoUrl() {
        return videoUrl;
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private ContentType type;
        private String text;
        private ImageUrl imageUrl;
        private VideoUrl videoUrl;

        private Builder() {}

        public Builder type(ContentType val) {
            type = val;
            return this;
        }

        public Builder text(String val) {
            text = val;
            return this;
        }

        public Builder imageUrl(ImageUrl val) {
            imageUrl = val;
            return this;
        }

        public Builder videoUrl(VideoUrl val) {
            videoUrl = val;
            return this;
        }

        public Content build() {
            return new Content(this);
        }
    }
}
