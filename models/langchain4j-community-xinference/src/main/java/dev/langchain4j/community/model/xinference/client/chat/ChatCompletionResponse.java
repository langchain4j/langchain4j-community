package dev.langchain4j.community.model.xinference.client.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.community.model.xinference.client.shared.CompletionUsage;

import java.util.List;

@JsonDeserialize(builder = ChatCompletionResponse.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class ChatCompletionResponse {
    private final String id;
    private final Integer created;
    private final String model;
    private final List<ChatCompletionChoice> choices;
    private final CompletionUsage usage;

    private ChatCompletionResponse(Builder builder) {
        id = builder.id;
        created = builder.created;
        model = builder.model;
        choices = builder.choices;
        usage = builder.usage;
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

    public List<ChatCompletionChoice> getChoices() {
        return choices;
    }

    public CompletionUsage getUsage() {
        return usage;
    }

    public String content() {
        return choices.get(0).getMessage().getContent();
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String id;
        private Integer created;
        private String model;
        private List<ChatCompletionChoice> choices;
        private CompletionUsage usage;

        private Builder() {
        }

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

        public Builder choices(List<ChatCompletionChoice> val) {
            choices = val;
            return this;
        }

        public Builder usage(CompletionUsage val) {
            usage = val;
            return this;
        }

        public ChatCompletionResponse build() {
            return new ChatCompletionResponse(this);
        }
    }
}
