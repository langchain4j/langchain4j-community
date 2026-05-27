package dev.langchain4j.community.model.client.transcription;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.internal.Utils.quoted;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import java.util.Objects;

@JsonDeserialize(builder = CohereTranscriptionResponse.Builder.class)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereTranscriptionResponse {

    private final String text;

    private CohereTranscriptionResponse(Builder builder) {
        this.text = builder.text;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return "CohereTranscriptionResponse{ text = " + quoted(text) + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereTranscriptionResponse other && Objects.equals(text, other.text);
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String text;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public CohereTranscriptionResponse build() {
            return new CohereTranscriptionResponse(this);
        }
    }
}
