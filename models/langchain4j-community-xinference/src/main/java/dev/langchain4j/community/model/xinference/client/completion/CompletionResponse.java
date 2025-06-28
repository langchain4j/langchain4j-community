package dev.langchain4j.community.model.xinference.client.completion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.community.model.xinference.client.shared.CompletionUsage;
import java.util.List;

@JsonDeserialize(builder = CompletionResponse.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class CompletionResponse {
    private final String id;
    private final Integer created;
    private final String model;
    private final List<CompletionChoice> choices;
    private final CompletionUsage usage;

    private CompletionResponse(Builder builder) {
        id = builder.id;
        created = builder.created;
        model = builder.model;
        choices = builder.choices;
        usage = builder.usage;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getId() {
        return id;
    }

    public Integer getCreated() {
        return created;
    }

    public String getModel() {
        return model;
    }

    public List<CompletionChoice> getChoices() {
        return choices;
    }

    public CompletionUsage getUsage() {
        return usage;
    }

    public String text() {
        return getChoices().get(0).getText();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String id;
        private Integer created;
        private String model;
        private List<CompletionChoice> choices;
        private CompletionUsage usage;

        private Builder() {}

        public Builder id(String val) {
            id = val;
            return this;
        }

        public Builder created(Integer val) {
            created = val;
            return this;
        }

        public Builder model(String val) {
            model = val;
            return this;
        }

        public Builder choices(List<CompletionChoice> val) {
            choices = val;
            return this;
        }

        public Builder usage(CompletionUsage val) {
            usage = val;
            return this;
        }

        public CompletionResponse build() {
            return new CompletionResponse(this);
        }
    }
}
