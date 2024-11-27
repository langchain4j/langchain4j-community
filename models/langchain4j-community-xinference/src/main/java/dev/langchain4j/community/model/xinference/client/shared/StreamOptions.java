package dev.langchain4j.community.model.xinference.client.shared;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(builder = StreamOptions.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class StreamOptions {
    private final Boolean includeUsage;

    private StreamOptions(Builder builder) {
        includeUsage = builder.includeUsage;
    }

    public Boolean getIncludeUsage() {
        return includeUsage;
    }

    public static StreamOptions of(boolean includeUsage) {
        return builder().includeUsage(includeUsage).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private Boolean includeUsage;

        private Builder() {
        }

        public Builder includeUsage(Boolean val) {
            includeUsage = val;
            return this;
        }

        public StreamOptions build() {
            return new StreamOptions(this);
        }
    }
}
