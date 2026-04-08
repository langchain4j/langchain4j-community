package dev.langchain4j.community.model.client.chat.streaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.internal.Utils.quoted;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.community.model.client.chat.response.CohereUsage;
import java.util.Objects;

@JsonDeserialize(builder = CohereDelta.Builder.class)
@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereDelta {

    private final String error;
    private final CohereStreamingMessage message;
    private final String finishReason;
    private final CohereUsage usage;

    private CohereDelta(Builder builder) {
        this.error = builder.error;
        this.message = builder.message;
        this.finishReason = builder.finishReason;
        this.usage = builder.usage;
    }

    public String getError() {
        return error;
    }

    public CohereStreamingMessage getMessage() {
        return message;
    }

    public String getFinishReason() {
        return finishReason;
    }

    public CohereUsage getUsage() {
        return usage;
    }

    @Override
    public String toString() {
        return "CohereDelta{"
                + "error=" + quoted(error)
                + ", message=" + message
                + ", finishReason=" + quoted(finishReason)
                + ", usage=" + usage
                + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(error, message, finishReason, usage);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereDelta other && equalsTo(other);
    }

    private boolean equalsTo(CohereDelta that) {
        return Objects.equals(error, that.error)
                && Objects.equals(message, that.message)
                && Objects.equals(finishReason, that.finishReason)
                && Objects.equals(usage, that.usage);
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String error;
        private CohereStreamingMessage message;
        private String finishReason;
        private CohereUsage usage;

        public Builder error(String error) {
            this.error = error;
            return this;
        }

        public Builder message(CohereStreamingMessage message) {
            this.message = message;
            return this;
        }

        public Builder finishReason(String finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        public Builder usage(CohereUsage usage) {
            this.usage = usage;
            return this;
        }

        public CohereDelta build() {
            return new CohereDelta(this);
        }
    }
}
