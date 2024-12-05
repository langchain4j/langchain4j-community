package dev.langchain4j.community.model.xinference.client.completion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.community.model.xinference.client.shared.StreamOptions;
import java.util.List;

@JsonDeserialize(builder = CompletionRequest.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class CompletionRequest {
    private final String model;
    private final String prompt;
    private final Integer maxTokens;
    private final Double temperature;
    private final Double topP;
    private final Integer n;
    private final Boolean stream;
    private final StreamOptions streamOptions;
    private final Integer logprobs;
    private final Boolean echo;
    private final List<String> stop;
    private final Double presencePenalty;
    private final Double frequencyPenalty;
    private final String user;

    private CompletionRequest(Builder builder) {
        model = builder.model;
        prompt = builder.prompt;
        maxTokens = builder.maxTokens;
        temperature = builder.temperature;
        topP = builder.topP;
        n = builder.n;
        stream = builder.stream;
        streamOptions = builder.streamOptions;
        logprobs = builder.logprobs;
        echo = builder.echo;
        stop = builder.stop;
        presencePenalty = builder.presencePenalty;
        frequencyPenalty = builder.frequencyPenalty;
        user = builder.user;
    }

    public String getModel() {
        return model;
    }

    public String getPrompt() {
        return prompt;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public Double getTemperature() {
        return temperature;
    }

    public Double getTopP() {
        return topP;
    }

    public Integer getN() {
        return n;
    }

    public Boolean getStream() {
        return stream;
    }

    public StreamOptions getStreamOptions() {
        return streamOptions;
    }

    public Integer getLogprobs() {
        return logprobs;
    }

    public Boolean getEcho() {
        return echo;
    }

    public List<String> getStop() {
        return stop;
    }

    public Double getPresencePenalty() {
        return presencePenalty;
    }

    public Double getFrequencyPenalty() {
        return frequencyPenalty;
    }

    public String getUser() {
        return user;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String model;
        private String prompt;
        private Integer maxTokens;
        private Double temperature;
        private Double topP;
        private Integer n;
        private Boolean stream;
        private StreamOptions streamOptions;
        private Integer logprobs;
        private Boolean echo;
        private List<String> stop;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private String user;

        private Builder() {}

        public Builder from(CompletionRequest request) {
            this.model(request.getModel());
            this.prompt(request.getPrompt());
            this.maxTokens(request.getMaxTokens());
            this.temperature(request.getTemperature());
            this.topP(request.getTopP());
            this.n(request.getN());
            this.stream(request.getStream());
            this.streamOptions(request.getStreamOptions());
            this.logprobs(request.getLogprobs());
            this.echo(request.getEcho());
            this.stop(request.getStop());
            this.presencePenalty(request.getPresencePenalty());
            this.frequencyPenalty(request.getFrequencyPenalty());
            this.user(request.getUser());
            return this;
        }

        public Builder model(String val) {
            model = val;
            return this;
        }

        public Builder prompt(String val) {
            prompt = val;
            return this;
        }

        public Builder maxTokens(Integer val) {
            maxTokens = val;
            return this;
        }

        public Builder temperature(Double val) {
            temperature = val;
            return this;
        }

        public Builder topP(Double val) {
            topP = val;
            return this;
        }

        public Builder n(Integer val) {
            n = val;
            return this;
        }

        public Builder stream(Boolean val) {
            stream = val;
            return this;
        }

        public Builder streamOptions(StreamOptions val) {
            streamOptions = val;
            return this;
        }

        public Builder logprobs(Integer val) {
            logprobs = val;
            return this;
        }

        public Builder echo(Boolean val) {
            echo = val;
            return this;
        }

        public Builder stop(List<String> val) {
            stop = val;
            return this;
        }

        public Builder presencePenalty(Double val) {
            presencePenalty = val;
            return this;
        }

        public Builder frequencyPenalty(Double val) {
            frequencyPenalty = val;
            return this;
        }

        public Builder user(String val) {
            user = val;
            return this;
        }

        public CompletionRequest build() {
            return new CompletionRequest(this);
        }
    }
}
