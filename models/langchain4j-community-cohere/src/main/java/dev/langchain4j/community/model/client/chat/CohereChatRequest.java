package dev.langchain4j.community.model.client.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.community.model.client.CohereSafetyMode;
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

    private final String model;
    private final List<CohereMessage> messages;
    private final CohereResponseFormat responseFormat;
    private final List<CohereTool> tools;
    private final ToolChoice toolChoice;
    private final Double temperature;
    private final Double p;
    private final Integer k;
    private final Double presencePenalty;
    private final Double frequencyPenalty;
    private final Integer maxTokens;
    private final List<String> stopSequences;
    private final CohereThinking thinking;
    private final Boolean stream;
    private final CohereSafetyMode safetyMode;
    private final Integer priority;
    private final Integer seed;
    private final Boolean logprobs;
    private final Boolean strictTools;

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
        this.logprobs = builder.logprobs;
        this.strictTools = builder.strictTools;
    }

    public String getModel() {
        return model;
    }

    public List<CohereMessage> getMessages() { return messages; }

    public CohereResponseFormat getResponseFormat() { return responseFormat; }

    public List<CohereTool> getTools() { return tools; }

    public ToolChoice getToolChoice() { return toolChoice; }

    public Double getTemperature() { return this.temperature; }

    public Double getP() { return this.p; }

    public Integer getK() { return this.k; }

    public Double getPresencePenalty() { return this.presencePenalty; }

    public Double getFrequencyPenalty() { return this.frequencyPenalty; }

    public Integer getMaxTokens() { return this.maxTokens; }

    public List<String> getStopSequences() { return this.stopSequences; }

    public CohereThinking getThinking() { return thinking; }

    public Boolean isStream() { return stream; }

    public CohereSafetyMode getSafetyMode() { return safetyMode; }

    public Integer getPriority() { return priority; }

    public Integer getSeed() { return seed; }

    public Boolean hasLogprobs() { return logprobs; }

    public Boolean hasStrictTools() { return strictTools; }

    @Override
    public String toString() {
        return "CohereChatRequest{"
                + "model=" + quoted(model)
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
                + ", logprobs=" + logprobs
                + ", strictTools=" + strictTools
                + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                model, messages, responseFormat, tools, toolChoice, temperature, p, k,
                presencePenalty, frequencyPenalty, maxTokens, stopSequences, thinking, stream,
                safetyMode, priority, seed, logprobs, strictTools);
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
                && Objects.equals(seed, that.seed)
                && Objects.equals(logprobs, that.logprobs)
                && Objects.equals(strictTools, that.strictTools);
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
        private CohereSafetyMode safetyMode;
        private Integer priority;
        private Integer seed;
        private Boolean logprobs;
        private Boolean strictTools;

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

        public Builder safetyMode(CohereSafetyMode safetyMode) {
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

        public Builder logprobs(Boolean logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public Builder strictTools(Boolean strictTools) {
            this.strictTools = strictTools;
            return this;
        }

        public CohereChatRequest build() {
            return new CohereChatRequest(this);
        }
    }
}
