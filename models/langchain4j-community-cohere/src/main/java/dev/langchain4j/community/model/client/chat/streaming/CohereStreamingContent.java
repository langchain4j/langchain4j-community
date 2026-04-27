package dev.langchain4j.community.model.client.chat.streaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.internal.Utils.quoted;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.community.model.client.chat.content.CohereContentType;
import java.util.Objects;

@JsonDeserialize(builder = CohereStreamingContent.Builder.class)
@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereStreamingContent {

    private final CohereContentType type;
    private final String text;
    private final String thinking;

    private CohereStreamingContent(Builder builder) {
        this.type = builder.type;
        this.text = builder.text;
        this.thinking = builder.thinking;
    }

    public CohereContentType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public String getThinking() {
        return thinking;
    }

    @Override
    public String toString() {
        return "CohereStreamingContent{"
                + "type=" + type
                + ", text=" + quoted(text)
                + ", thinking=" + quoted(thinking)
                + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, text, thinking);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereStreamingContent message && equalsTo(message);
    }

    private boolean equalsTo(CohereStreamingContent that) {
        return Objects.equals(type, that.type)
                && Objects.equals(text, that.text)
                && Objects.equals(thinking, that.thinking);
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private CohereContentType type;
        private String text;
        private String thinking;

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

        public CohereStreamingContent build() {
            return new CohereStreamingContent(this);
        }
    }
}
