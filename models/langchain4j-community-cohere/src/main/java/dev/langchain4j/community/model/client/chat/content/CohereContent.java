package dev.langchain4j.community.model.client.chat.content;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.community.model.client.chat.message.content.CohereImageUrl;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.community.model.client.chat.content.CohereContentType.IMAGE_URL;
import static dev.langchain4j.community.model.client.chat.content.CohereContentType.TEXT;
import static dev.langchain4j.community.model.client.chat.content.CohereContentType.THINKING;

@JsonDeserialize(builder = CohereContent.Builder.class)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereContent {

    private final CohereContentType type;
    private final String text;
    private final String thinking;
    private final CohereImageUrl imageUrl;

    private CohereContent(Builder builder) {
        this.type = builder.type;
        this.text = builder.text;
        this.thinking = builder.thinking;
        this.imageUrl = builder.imageUrl;
    }

    public CohereContentType getType() { return type; }

    public String getText() { return text; }

    public String getThinking() { return thinking; }

    public CohereImageUrl getImageUrl() { return imageUrl; }

    public static CohereContent text(String text) {
        return builder().type(TEXT).text(text).build();
    }

    public static CohereContent thinking(String thinking) {
        return builder().type(THINKING).thinking(thinking).build();
    }

    public static CohereContent image(CohereImageUrl imageUrl) {
        return builder().type(IMAGE_URL).imageUrl(imageUrl).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "CohereContent{ "
                + "type = " + type
                + ", text = " + text
                + ", thinking = " + thinking
                + ", imageUrl = " + imageUrl
                + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, text, thinking, imageUrl);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereContent that && equalsTo(that);
    }

    private boolean equalsTo(CohereContent that) {
        return Objects.equals(type, that.type)
                && Objects.equals(text, that.text)
                && Objects.equals(thinking, that.thinking)
                && Objects.equals(imageUrl, that.imageUrl);
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonInclude(NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private CohereContentType type;
        private String text;
        private String thinking;
        private CohereImageUrl imageUrl;

        public Builder type(CohereContentType type) {
            this.type = type;
            return this;
        }

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder thinking(String thinking) {
            this.thinking = thinking;
            return this;
        }

        public Builder imageUrl(CohereImageUrl imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public CohereContent build() {
            return new CohereContent(this);
        }
    }
}
