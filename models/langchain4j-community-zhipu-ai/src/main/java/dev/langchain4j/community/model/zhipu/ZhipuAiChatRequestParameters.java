package dev.langchain4j.community.model.zhipu;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.quoted;

import dev.langchain4j.community.model.zhipu.chat.Thinking;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import java.util.Objects;

public class ZhipuAiChatRequestParameters extends DefaultChatRequestParameters {

    private final Boolean doSample;
    private final Boolean toolStream;
    private final Thinking thinking;

    protected ZhipuAiChatRequestParameters(Builder builder) {
        super(builder);
        this.doSample = builder.doSample;
        this.toolStream = builder.toolStream;
        this.thinking = builder.thinking;
    }

    public Boolean doSample() {
        return doSample;
    }

    public Boolean toolStream() {
        return toolStream;
    }

    public Thinking thinking() {
        return thinking;
    }

    @Override
    public ZhipuAiChatRequestParameters overrideWith(ChatRequestParameters that) {
        return ZhipuAiChatRequestParameters.builder()
                .overrideWith(this)
                .overrideWith(that)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ZhipuAiChatRequestParameters that)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(doSample, that.doSample)
                && Objects.equals(toolStream, that.toolStream)
                && Objects.equals(thinking, that.thinking);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), doSample, toolStream, thinking);
    }

    @Override
    public String toString() {
        return "ZhipuAiChatRequestParameters{" + "modelName="
                + quoted(modelName()) + ", temperature="
                + temperature() + ", topP="
                + topP() + ", topK="
                + topK() + ", frequencyPenalty="
                + frequencyPenalty() + ", presencePenalty="
                + presencePenalty() + ", maxOutputTokens="
                + maxOutputTokens() + ", stopSequences="
                + stopSequences() + ", toolSpecifications="
                + toolSpecifications() + ", toolChoice="
                + toolChoice() + ", responseFormat="
                + responseFormat() + ", doSample="
                + doSample + ", toolStream="
                + toolStream + ", thinking="
                + thinking + '}';
    }

    public static class Builder extends DefaultChatRequestParameters.Builder<Builder> {
        private Boolean doSample;
        private Boolean toolStream;
        private Thinking thinking;

        @Override
        public Builder overrideWith(ChatRequestParameters parameters) {
            super.overrideWith(parameters);
            if (parameters instanceof ZhipuAiChatRequestParameters zhipuParameters) {
                doSample(getOrDefault(zhipuParameters.doSample(), doSample));
                toolStream(getOrDefault(zhipuParameters.toolStream(), toolStream));
                thinking(getOrDefault(zhipuParameters.thinking(), thinking));
            }
            return this;
        }

        public Builder doSample(Boolean doSample) {
            this.doSample = doSample;
            return this;
        }

        public Builder toolStream(Boolean toolStream) {
            this.toolStream = toolStream;
            return this;
        }

        public Builder thinking(Thinking thinking) {
            this.thinking = thinking;
            return this;
        }

        @Override
        public ZhipuAiChatRequestParameters build() {
            return new ZhipuAiChatRequestParameters(this);
        }
    }
}
