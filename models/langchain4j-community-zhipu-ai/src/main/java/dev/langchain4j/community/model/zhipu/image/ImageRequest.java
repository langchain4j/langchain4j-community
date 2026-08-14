package dev.langchain4j.community.model.zhipu.image;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ImageRequest {
    private String prompt;
    private String model;
    private String userId;

    public ImageRequest(String prompt, String model, String userId) {
        this.prompt = prompt;
        this.model = model;
        this.userId = userId;
    }

    public static ImageRequestBuilder builder() {
        return new ImageRequestBuilder();
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public static class ImageRequestBuilder {
        private String prompt;
        private String model;
        private String userId;

        ImageRequestBuilder() {}

        public ImageRequestBuilder prompt(String prompt) {
            this.prompt = prompt;
            return this;
        }

        public ImageRequestBuilder model(String model) {
            this.model = model;
            return this;
        }

        public ImageRequestBuilder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public ImageRequest build() {
            return new ImageRequest(this.prompt, this.model, this.userId);
        }

        public String toString() {
            return "ImageRequest.ImageRequestBuilder(prompt=" + this.prompt + ", model=" + this.model + ", userId="
                    + this.userId + ")";
        }
    }
}
