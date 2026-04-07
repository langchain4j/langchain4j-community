package dev.langchain4j.community.model.client.chat.response;

import dev.langchain4j.model.chat.response.ChatResponseMetadata;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

public final class CohereChatResponseMetadata extends ChatResponseMetadata {

    private final List<CohereLogprobs> logprobs;

    private CohereChatResponseMetadata(final Builder builder) {
        super(builder);
        logprobs = builder.logprobs;
    }

    public List<CohereLogprobs> logprobs() {
        return logprobs;
    }

    @Override
    public Builder toBuilder() {
        return ((Builder) super.toBuilder(builder())).logprobs(logprobs);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereChatResponseMetadata that
                && super.equals(that)
                && Objects.equals(logprobs, that.logprobs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), logprobs);
    }

    @Override
    public String toString() {
        return "CohereChatResponseMetadata{"
                + "id=" + quoted(id())
                + ", modelName=" + quoted(modelName())
                + ", tokenUsage=" + tokenUsage()
                + ", finishReason=" + finishReason()
                + ", logprobs=" + logprobs
                + '}';
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private List<CohereLogprobs> logprobs;

        public Builder logprobs(List<CohereLogprobs> logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        @Override
        public CohereChatResponseMetadata build() {
            return new CohereChatResponseMetadata(this);
        }
    }
}
