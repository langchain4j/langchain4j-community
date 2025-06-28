package dev.langchain4j.community.model.qianfan.client.chat;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class FunctionCall {

    private String name;
    private String thoughts;
    private String arguments;

    public FunctionCall() {}

    private FunctionCall(Builder builder) {
        this.name = builder.name;
        this.thoughts = builder.thoughts;
        this.arguments = builder.arguments;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getThoughts() {
        return thoughts;
    }

    public void setThoughts(final String thoughts) {
        this.thoughts = thoughts;
    }

    public String getArguments() {
        return arguments;
    }

    public void setArguments(final String arguments) {
        this.arguments = arguments;
    }

    public boolean equals(Object another) {
        if (this == another) {
            return true;
        } else {
            return another instanceof FunctionCall && this.equalTo((FunctionCall) another);
        }
    }

    private boolean equalTo(FunctionCall another) {
        return Objects.equals(this.name, another.name)
                && Objects.equals(this.arguments, another.arguments)
                && Objects.equals(this.thoughts, another.thoughts);
    }

    @Override
    public String toString() {
        return "{" + "name='"
                + name + '\'' + ", thoughts='"
                + thoughts + '\'' + ", arguments='"
                + arguments + '\'' + '}';
    }

    public int hashCode() {
        int h = 5381;
        h += (h << 5) + Objects.hashCode(this.name);
        h += (h << 5) + Objects.hashCode(this.arguments);
        h += (h << 5) + Objects.hashCode(this.thoughts);
        return h;
    }

    public static final class Builder {

        private String name;
        private String thoughts;
        private String arguments;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder thoughts(String thoughts) {
            this.thoughts = thoughts;
            return this;
        }

        public Builder arguments(String arguments) {
            this.arguments = arguments;
            return this;
        }

        public FunctionCall build() {
            return new FunctionCall(this);
        }
    }
}
