package dev.langchain4j.community.model.client.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.community.model.client.chat.message.CohereMessage;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereChatRequest {

    private String model;
    private List<CohereMessage> messages;

    public CohereChatRequest(Builder builder) {
        this.model = builder.model;
        this.messages = builder.messages;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<CohereMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<CohereMessage> messages) {
        this.messages = messages;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String model;
        private List<CohereMessage> messages;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder messages(List<CohereMessage> message) {
            this.messages = message;
            return this;
        }

        public CohereChatRequest build() {
            return new CohereChatRequest(this);
        }
    }
}
