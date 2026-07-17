package dev.langchain4j.community.model.zhipu.chat;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class Thinking {

    private final String type;
    private final Boolean clearThinking;

    public Thinking(Builder builder) {
        this.type = builder.type;
        this.clearThinking = builder.clearThinking;
    }

    public String getType() {
        return type;
    }

    public Boolean clearThinking() {
        return clearThinking;
    }

    public Boolean isClearThinking() {
        return clearThinking;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String type;
        private Boolean clearThinking;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder clearThinking(Boolean clearThinking) {
            this.clearThinking = clearThinking;
            return this;
        }

        public Thinking build() {
            return new Thinking(this);
        }
    }
}
