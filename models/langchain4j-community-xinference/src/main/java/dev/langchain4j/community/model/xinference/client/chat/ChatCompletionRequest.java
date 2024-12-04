package dev.langchain4j.community.model.xinference.client.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.community.model.xinference.client.chat.message.Message;
import dev.langchain4j.community.model.xinference.client.shared.StreamOptions;
import java.util.List;

@JsonDeserialize(builder = ChatCompletionRequest.Builder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class ChatCompletionRequest {
    private final String model;
    private final List<Message> messages;
    private final Double temperature;
    private final Double topP;
    private final Integer n;
    private final Boolean stream;
    private final StreamOptions streamOptions;
    private final List<String> stop;
    private final Integer maxTokens;
    private final Double presencePenalty;
    private final Double frequencyPenalty;
    private final String user;
    private final Integer seed;
    private final List<Tool> tools;
    private final Object toolChoice;
    private final Boolean parallelToolCalls;

    private ChatCompletionRequest(Builder builder) {
        model = builder.model;
        messages = builder.messages;
        temperature = builder.temperature;
        topP = builder.topP;
        n = builder.n;
        stream = builder.stream;
        streamOptions = builder.streamOptions;
        stop = builder.stop;
        maxTokens = builder.maxTokens;
        presencePenalty = builder.presencePenalty;
        frequencyPenalty = builder.frequencyPenalty;
        user = builder.user;
        seed = builder.seed;
        tools = builder.tools;
        toolChoice = builder.toolChoice;
        parallelToolCalls = builder.parallelToolCalls;
    }

    public String getModel() {
        return model;
    }

    public List<Message> getMessages() {
        return messages;
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

    public List<String> getStop() {
        return stop;
    }

    public Integer getMaxTokens() {
        return maxTokens;
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

    public Integer getSeed() {
        return seed;
    }

    public List<Tool> getTools() {
        return tools;
    }

    public Object getToolChoice() {
        return toolChoice;
    }

    public Boolean getParallelToolCalls() {
        return parallelToolCalls;
    }

    public static Builder builder() {
        return new Builder();
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static final class Builder {
        private String model;
        private List<Message> messages;
        private Double temperature;
        private Double topP;
        private Integer n;
        private Boolean stream;
        private StreamOptions streamOptions;
        private List<String> stop;
        private Integer maxTokens;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private String user;
        private Integer seed;
        private List<Tool> tools;
        private Object toolChoice;
        private Boolean parallelToolCalls;

        private Builder() {}

        public Builder from(ChatCompletionRequest request) {
            this.model(request.getModel());
            this.messages(request.getMessages());
            this.temperature(request.getTemperature());
            this.topP(request.getTopP());
            this.n(request.getN());
            this.stream(request.getStream());
            this.streamOptions(request.getStreamOptions());
            this.stop(request.getStop());
            this.maxTokens(request.getMaxTokens());
            this.presencePenalty(request.getPresencePenalty());
            this.frequencyPenalty(request.getFrequencyPenalty());
            this.user(request.getUser());
            this.seed(request.getSeed());
            this.tools(request.getTools());
            this.toolChoice(request.getToolChoice());
            this.parallelToolCalls(request.getParallelToolCalls());
            return this;
        }

        public Builder model(String val) {
            model = val;
            return this;
        }

        public Builder messages(List<Message> val) {
            messages = val;
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

        public Builder stop(List<String> val) {
            stop = val;
            return this;
        }

        public Builder maxTokens(Integer val) {
            maxTokens = val;
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

        public Builder seed(Integer val) {
            seed = val;
            return this;
        }

        public Builder tools(List<Tool> val) {
            tools = val;
            return this;
        }

        public Builder toolChoice(Object val) {
            toolChoice = val;
            return this;
        }

        public Builder parallelToolCalls(Boolean val) {
            parallelToolCalls = val;
            return this;
        }

        public ChatCompletionRequest build() {
            return new ChatCompletionRequest(this);
        }
    }
}
