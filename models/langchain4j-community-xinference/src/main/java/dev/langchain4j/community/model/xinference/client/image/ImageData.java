package dev.langchain4j.community.model.xinference.client.image;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = ImageData.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class ImageData {
    private final String url;
    private final String b64Json;

    private ImageData(Builder builder) {
        url = builder.url;
        b64Json = builder.b64Json;
    }

    public String getUrl() {
        return url;
    }

    public String getB64Json() {
        return b64Json;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String url;
        private String b64Json;

        private Builder() {
        }

        public Builder url(String val) {
            url = val;
            return this;
        }

        public Builder b64Json(String val) {
            b64Json = val;
            return this;
        }

        public ImageData build() {
            return new ImageData(this);
        }
    }
}
