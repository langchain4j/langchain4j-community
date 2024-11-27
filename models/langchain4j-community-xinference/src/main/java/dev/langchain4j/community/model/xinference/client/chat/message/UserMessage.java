package dev.langchain4j.community.model.xinference.client.chat.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.community.model.xinference.client.chat.Role;

@JsonDeserialize(builder = UserMessage.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class UserMessage implements Message {
    private final Role role = Role.USER;
    private final Object content;
    private final String name;

    private UserMessage(Builder builder) {
        content = builder.content;
        name = builder.name;
    }

    @Override
    public Role getRole() {
        return role;
    }

    public Object getContent() {
        return content;
    }

    public String getName() {
        return name;
    }

    public static UserMessage of(String content) {
        return builder().content(content).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private Object content;
        private String name;

        private Builder() {
        }

        public Builder content(Object val) {
            content = val;
            return this;
        }

        public Builder name(String val) {
            name = val;
            return this;
        }

        public UserMessage build() {
            return new UserMessage(this);
        }
    }
}
