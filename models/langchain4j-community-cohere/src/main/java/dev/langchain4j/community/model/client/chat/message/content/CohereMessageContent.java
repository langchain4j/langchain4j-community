package dev.langchain4j.community.model.client.chat.message.content;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereMessageContent {

    public CohereContentType type;

    public String text;

    public String thinking;

    public CohereMessageContent() {}

    private CohereMessageContent(Builder builder) {
        this.type = type;
        this.text = text;
        this.thinking = thinking;
    }

    public Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        CohereMessageContent that = (CohereMessageContent) o;
        return this.type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type);
    }

    public static class Builder {

        private CohereContentType type;
        private String text;
        private String thinking;

        public Builder CohereContentType(CohereContentType type) {
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

        public CohereMessageContent build() {
            return new CohereMessageContent(this);
        }
    }
}
