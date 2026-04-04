package dev.langchain4j.community.model.client.chat.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonDeserialize(builder = CohereChatResponse.Builder.class)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereChatResponse {

    private final String id;
    private final CohereResponseMessage message;
    private final String finishReason;
    private final CohereUsage usage;

    private CohereChatResponse(Builder builder) {
        this.id = builder.id;
        this.message = builder.message;
        this.finishReason = builder.finishReason;
        this.usage = builder.usage;
    }

    public String getId() { return id; }

    public CohereResponseMessage getMessage() { return message; }

    public String getFinishReason() { return finishReason; }

    public CohereUsage getUsage() { return usage; }

    @Override
    public String toString() {
        return "CohereChatResponse{ "
                + "id = " + id
                + ", message = " + message
                + ", finishReason = " + finishReason
                + ", usage = " + usage
                + " }";
    }

    @Override
    public int hashCode() { return Objects.hash(id, message, finishReason, usage); }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereChatResponse that && equalsTo(that);
    }

    private boolean equalsTo(CohereChatResponse that) {
        return Objects.equals(id, that.id)
                && Objects.equals(message, that.message)
                && Objects.equals(finishReason, that.finishReason)
                && Objects.equals(usage, that.usage);
    }

    public static Builder builder() { return new Builder(); }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonInclude(NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {

        private String id;
        private CohereResponseMessage message;
        private String finishReason;
        private CohereUsage usage;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder message(CohereResponseMessage message) {
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

        public CohereChatResponse build() { return new CohereChatResponse(this); }
    }
}
