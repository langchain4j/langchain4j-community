package dev.langchain4j.community.model.xinference.client.image;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = ImageRequest.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class ImageRequest {
    private final String model;
    private final String prompt;
    private final String negativePrompt;
    private final Integer n;
    private final ResponseFormat responseFormat;
    private final String size;
    private final String kwargs;
    private final String user;

    private ImageRequest(Builder builder) {
        model = builder.model;
        prompt = builder.prompt;
        negativePrompt = builder.negativePrompt;
        n = builder.n;
        responseFormat = builder.responseFormat;
        size = builder.size;
        kwargs = builder.kwargs;
        user = builder.user;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getModel() {
        return model;
    }

    public String getPrompt() {
        return prompt;
    }

    public String getNegativePrompt() {
        return negativePrompt;
    }

    public Integer getN() {
        return n;
    }

    public ResponseFormat getResponseFormat() {
        return responseFormat;
    }

    public String getSize() {
        return size;
    }

    public String getKwargs() {
        return kwargs;
    }

    public String getUser() {
        return user;
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String model;
        private String prompt;
        private String negativePrompt;
        private Integer n = 1;
        private ResponseFormat responseFormat;
        private String size;
        private String kwargs;
        private String user;

        private Builder() {}

        public Builder model(String val) {
            model = val;
            return this;
        }

        public Builder prompt(String val) {
            prompt = val;
            return this;
        }

        public Builder negativePrompt(String val) {
            negativePrompt = val;
            return this;
        }

        public Builder n(Integer val) {
            n = val;
            return this;
        }

        public Builder responseFormat(ResponseFormat val) {
            responseFormat = val;
            return this;
        }

        public Builder size(String val) {
            size = val;
            return this;
        }

        public Builder kwargs(String val) {
            kwargs = val;
            return this;
        }

        public Builder user(String val) {
            user = val;
            return this;
        }

        public ImageRequest build() {
            return new ImageRequest(this);
        }
    }
}
