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
    private Double temperature;
    private Double topP;
    private Integer topK;
    private Double presencePenalty;
    private Double frequencyPenalty;
    private Integer maxOutputTokens;
    private List<String> stopSequences;

    public CohereChatRequest(Builder builder) {
        this.model = builder.model;
        this.messages = builder.messages;
        this.temperature = builder.temperature;
        this.topP = builder.topP;
        this.topK = builder.topK;
        this.presencePenalty = builder.presencePenalty;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.stopSequences = builder.stopSequences;
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

    public void setMessages(List<CohereMessage> messages) { this.messages = messages; }

    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Double getTemperature() { return this.temperature; }

    public void setTopP(Double topP) { this.topP = topP; }

    public Double getTopP() { return this.topP; }

    public void setTopK(Integer topK) { this.topK = topK; }

    public Integer getTopK() { return this.topK; }

    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public Double getPresencePenalty() { return this.presencePenalty; }

    public void setFrequencyPenalty(Double frequencyPenalty ) {
        this.frequencyPenalty = frequencyPenalty;
    }

    public Double getFrequencyPenalty() { return this.frequencyPenalty; }

    public void setMaxOutputTokens(Integer maxOutputTokens) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public Integer getMaxOutputTokens() { return this.maxOutputTokens; }

    public void setStopSequences(List<String> stopSequences) {
        this.stopSequences = stopSequences;
    }

    public List<String> getStopSequences() { return this.stopSequences; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String model;
        private List<CohereMessage> messages;
        private Double temperature;
        private Double topP;
        private Integer topK;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Integer maxOutputTokens;
        private List<String> stopSequences;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder messages(List<CohereMessage> message) {
            this.messages = message;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public Builder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public Builder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public Builder maxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public CohereChatRequest build() {
            return new CohereChatRequest(this);
        }
    }
}
