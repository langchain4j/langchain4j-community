package dev.langchain4j.community.model.client.chat.thinking;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.internal.Utils.getOrDefault;

@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereThinking {

    private final String type;
    private final Integer tokenBudget;

    private CohereThinking(Builder builder) {
        this.type = getOrDefault(builder.type, builder.type);
        this.tokenBudget = builder.tokenBudget;
    }

    public static Builder builder() { return new Builder(); }

    @Override
    public String toString() {
        return "CohereThinking{ "
                + "type = " + type
                + ", tokenBudget = " + tokenBudget
                + " }";
    }

    @Override
    public int hashCode() { return Objects.hash(type, tokenBudget); }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereThinking other && equalsTo(other);
    }

    private boolean equalsTo(CohereThinking that) {
        return Objects.equals(type, that.type) && Objects.equals(tokenBudget, that.tokenBudget);
    }

    public static class Builder {

        private String type;
        private Integer tokenBudget;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder tokenBudget(Integer tokenBudget) {
            this.tokenBudget = tokenBudget;
            return this;
        }

        public CohereThinking build() { return new CohereThinking(this); }
    }
}
