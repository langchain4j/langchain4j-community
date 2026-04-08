package dev.langchain4j.community.model.client.chat.streaming;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.community.model.client.chat.response.CohereLogprobs;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.internal.Utils.quoted;

@JsonDeserialize(builder = CohereStreamingData.Builder.class)
@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereStreamingData {

    private final String type;
    private final String id;
    private final CohereDelta delta;
    private final Integer index;
    private final CohereLogprobs logprobs;

    private CohereStreamingData(Builder builder) {
        this.type = builder.type;
        this.id = builder.id;
        this.delta = builder.delta;
        this.index = builder.index;
        this.logprobs = builder.logprobs;
    }

    public String getType() { return type; }

    public String getId() { return id; }

    public CohereDelta getDelta() { return delta; }

    public Integer getIndex() { return index; }

    public CohereLogprobs getLogprobs() { return logprobs; }

    @Override
    public String toString() {
        return "CohereStreamingData{"
                    + "type=" + quoted(type)
                    + ", id=" + quoted(id)
                    + ", delta=" + delta
                    + ", index=" + index
                    + ", logprobs=" + logprobs
                + '}';
    }

    @Override
    public int hashCode() { return Objects.hash(type, id, delta, index, logprobs); }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereStreamingData response && equalsTo(response);
    }

    private boolean equalsTo(CohereStreamingData that) {
        return Objects.equals(type, that.type)
                && Objects.equals(id, that.id)
                && Objects.equals(delta, that.delta)
                && Objects.equals(index, that.index)
                && Objects.equals(logprobs, that.logprobs);
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String type;
        private String id;
        private CohereDelta delta;
        private Integer index;
        private CohereLogprobs logprobs;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder delta(CohereDelta delta) {
            this.delta = delta;
            return this;
        }

        public Builder index(Integer index) {
            this.index = index;
            return this;
        }

        public Builder logprobs(CohereLogprobs logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public CohereStreamingData build() {
            return new CohereStreamingData(this);
        }
    }
}
