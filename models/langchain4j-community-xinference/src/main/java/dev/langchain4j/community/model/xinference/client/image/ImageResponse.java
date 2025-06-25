package dev.langchain4j.community.model.xinference.client.image;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.List;

@JsonDeserialize(builder = ImageResponse.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class ImageResponse {
    private final Integer created;
    private final List<ImageData> data;

    private ImageResponse(Builder builder) {
        created = builder.created;
        data = builder.data;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Integer getCreated() {
        return created;
    }

    public List<ImageData> getData() {
        return data;
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private Integer created;
        private List<ImageData> data;

        private Builder() {}

        public Builder created(Integer val) {
            created = val;
            return this;
        }

        public Builder data(List<ImageData> val) {
            data = val;
            return this;
        }

        public ImageResponse build() {
            return new ImageResponse(this);
        }
    }
}
