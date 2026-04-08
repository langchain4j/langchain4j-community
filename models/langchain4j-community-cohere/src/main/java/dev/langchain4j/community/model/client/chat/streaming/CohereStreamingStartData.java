package dev.langchain4j.community.model.client.chat.streaming;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

@JsonDeserialize(builder = CohereStreamingStartData.Builder.class)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereStreamingStartData {

    private final String id;

    private CohereStreamingStartData(Builder builder) {
        this.id = builder.id;
    }

    public String getId() { return id; }

    @Override
    public String toString() {
        return "CohereStreamingStartData{"
                + "id=" + quoted(id)
                + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CohereStreamingStartData that = (CohereStreamingStartData) o;
        return Objects.equals(id, that.id);
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String id;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public CohereStreamingStartData build() {
            return new CohereStreamingStartData(this);
        }
    }
}
