package dev.langchain4j.community.model.zhipu.chat;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResponseFormat {

    private final String type;

    public ResponseFormat(Builder builder) {
        this.type = builder.type;
    }

    public String getType() {
        return type;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private String type;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public ResponseFormat build() {
            return new ResponseFormat(this);
        }
    }
}
