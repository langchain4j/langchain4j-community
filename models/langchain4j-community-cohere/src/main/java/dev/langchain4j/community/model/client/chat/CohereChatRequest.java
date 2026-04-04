package dev.langchain4j.community.model.client.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.community.model.client.chat.message.CohereMessage;
import dev.langchain4j.community.model.client.chat.thinking.CohereThinking;
import dev.langchain4j.community.model.client.chat.tool.CohereTool;
import dev.langchain4j.model.chat.request.ToolChoice;

import java.util.List;
import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.internal.Utils.quoted;
import static java.util.Arrays.asList;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereChatRequest {

    private String model;
    private List<CohereMessage> messages;
    private CohereResponseFormat responseFormat;
    private List<CohereTool> tools;
    private ToolChoice toolChoice;
    private Double temperature;
    private Double p;
    private Integer k;
    private Double presencePenalty;
    private Double frequencyPenalty;
    private Integer maxTokens;
    private List<String> stopSequences;
    private CohereThinking thinking;
    private Boolean stream;
    private String safetyMode;
    private Integer priority;
    private Integer seed;

    public CohereChatRequest(Builder builder) {
        this.model = builder.model;
        this.messages = builder.messages;
        this.responseFormat = builder.responseFormat;
        this.tools = builder.tools;
        this.toolChoice = builder.toolChoice;
        this.temperature = builder.temperature;
        this.p = builder.p;
        this.k = builder.k;
        this.presencePenalty = builder.presencePenalty;
        this.frequencyPenalty = builder.frequencyPenalty;
        this.maxTokens = builder.maxTokens;
        this.stopSequences = builder.stopSequences;
        this.thinking = builder.thinking;
        this.stream = builder.stream;
        this.safetyMode = builder.safetyMode;
        this.priority = builder.priority;
        this.seed = builder.seed;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<CohereMessage> getMessages() { return messages; }

    public void setMessages(List<CohereMessage> messages) { this.messages = messages; }

    public void setResponseFormat(CohereResponseFormat responseFormat) {
        this.responseFormat = responseFormat;
    }

    public CohereResponseFormat getResponseFormat() { return responseFormat; }

    public List<CohereTool> getTools() { return tools; }

    public void setTools(List<CohereTool> tools) { this.tools = tools; }

    public ToolChoice getToolChoice() { return toolChoice; }

    public void setToolChoice(ToolChoice toolChoice) { this.toolChoice = toolChoice; }

    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Double getTemperature() { return this.temperature; }

    public void setP(Double p) { this.p = p; }

    public Double getP() { return this.p; }

    public void setK(Integer k) { this.k = k; }

    public Integer getK() { return this.k; }

    public void setPresencePenalty(Double presencePenalty) {
        this.presencePenalty = presencePenalty;
    }

    public Double getPresencePenalty() { return this.presencePenalty; }

    public void setFrequencyPenalty(Double frequencyPenalty ) {
        this.frequencyPenalty = frequencyPenalty;
    }

    public Double getFrequencyPenalty() { return this.frequencyPenalty; }

    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }

    public Integer getMaxTokens() { return this.maxTokens; }

    public void setStopSequences(List<String> stopSequences) { this.stopSequences = stopSequences; }

    public List<String> getStopSequences() { return this.stopSequences; }

    public void setThinking(CohereThinking thinking) { this.thinking = thinking; }

    public CohereThinking getThinking() { return thinking; }

    public void setStream(Boolean stream) { this.stream = stream; }

    public Boolean getStream() { return stream; }

    public void setSafetyMode(String safetyMode) { this.safetyMode = safetyMode; }

    public String getSafetyMode() { return safetyMode; }

    public void setPriority(Integer priority) { this.priority = priority; }

    public Integer getPriority() { return priority; }

    public void setSeed(Integer seed) { this.seed = seed; }

    public Integer getSeed() { return seed; }

    @Override
    public String toString() {
        return "CohereChatRequest{"
                + "model='" + quoted(model)
                + ", messages=" + messages
                + ", responseFormat=" + responseFormat
                + ", tools=" + tools
                + ", toolChoice=" + toolChoice
                + ", temperature=" + temperature
                + ", p=" + p
                + ", k=" + k
                + ", presencePenalty=" + presencePenalty
                + ", frequencyPenalty=" + frequencyPenalty
                + ", maxTokens=" + maxTokens
                + ", stopSequences=" + stopSequences
                + ", thinking=" + thinking
                + ", stream=" + stream
                + ", safetyMode=" + quoted(safetyMode)
                + ", priority=" + priority
                + ", seed=" + seed
                + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                model, messages, responseFormat, tools, toolChoice, temperature, p, k,
                presencePenalty, frequencyPenalty, maxTokens, stopSequences, thinking, stream,
                safetyMode, priority, seed);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereChatRequest that && equalsTo(that);
    }

    private boolean equalsTo(CohereChatRequest that) {
        return Objects.equals(model, that.model)
                && Objects.equals(messages, that.messages)
                && Objects.equals(responseFormat, that.responseFormat)
                && Objects.equals(tools, that.tools)
                && Objects.equals(toolChoice, that.toolChoice)
                && Objects.equals(temperature, that.temperature)
                && Objects.equals(p, that.p)
                && Objects.equals(k, that.k)
                && Objects.equals(presencePenalty, that.presencePenalty)
                && Objects.equals(frequencyPenalty, that.frequencyPenalty)
                && Objects.equals(maxTokens, that.maxTokens)
                && Objects.equals(stopSequences, that.stopSequences)
                && Objects.equals(thinking, that.thinking)
                && Objects.equals(stream, that.stream)
                && Objects.equals(safetyMode, that.safetyMode)
                && Objects.equals(priority, that.priority)
                && Objects.equals(seed, that.seed);
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {

        private String model;
        private List<CohereMessage> messages;
        private CohereResponseFormat responseFormat;
        private List<CohereTool> tools;
        private ToolChoice toolChoice;
        private Double temperature;
        private Double p;
        private Integer k;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private Integer maxTokens;
        private List<String> stopSequences;
        private CohereThinking thinking;
        private Boolean stream;
        private String safetyMode;
        private Integer priority;
        private Integer seed;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder messages(CohereMessage... messages) {
            return messages(asList(messages));
        }

        public Builder messages(List<CohereMessage> message) {
            this.messages = message;
            return this;
        }

        public Builder responseFormat(CohereResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public Builder tools(List<CohereTool> tools) {
            this.tools = tools;
            return this;
        }

        public Builder toolChoice(ToolChoice toolChoice) {
            this.toolChoice = toolChoice;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder p(Double p) {
            this.p = p;
            return this;
        }

        public Builder k(Integer k) {
            this.k = k;
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

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder stopSequences(List<String> stopSequences) {
            this.stopSequences = stopSequences;
            return this;
        }

        public Builder thinking(CohereThinking thinking) {
            this.thinking = thinking;
            return this;
        }

        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        public Builder safetyMode(String safetyMode) {
            this.safetyMode = safetyMode;
            return this;
        }

        public Builder priority(Integer priority) {
            this.priority = priority;
            return this;
        }

        public Builder seed(Integer seed) {
            this.seed = seed;
            return this;
        }

        public CohereChatRequest build() {
            return new CohereChatRequest(this);
        }
    }
}
