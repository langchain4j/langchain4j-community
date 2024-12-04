package dev.langchain4j.community.model.xinference.client.image;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = OcrRequest.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class OcrRequest {
    private final String model;
    private final byte[] image;
    private final String kwargs;

    private OcrRequest(Builder builder) {
        model = builder.model;
        image = builder.image;
        kwargs = builder.kwargs;
    }

    public String getModel() {
        return model;
    }

    public byte[] getImage() {
        return image;
    }

    public String getKwargs() {
        return kwargs;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String model;
        private byte[] image;
        private String kwargs;

        private Builder() {}

        public Builder model(String val) {
            model = val;
            return this;
        }

        public Builder image(byte[] val) {
            image = val;
            return this;
        }

        public Builder kwargs(String val) {
            kwargs = val;
            return this;
        }

        public OcrRequest build() {
            return new OcrRequest(this);
        }
    }
}
