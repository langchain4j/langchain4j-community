package dev.langchain4j.community.model.client.chat.response;

import dev.langchain4j.model.chat.response.ChatResponseMetadata;

import java.util.List;
import java.util.Objects;

import static dev.langchain4j.internal.Utils.quoted;

public final class CohereChatResponseMetadata extends ChatResponseMetadata {

    private final List<CohereLogprobs> logprobs;
    private final CohereBilledUnits billedUnits;
    private final Integer cachedTokens;

    private CohereChatResponseMetadata(final Builder builder) {
        super(builder);
        logprobs = builder.logprobs;
        billedUnits = builder.billedUnits;
        cachedTokens = builder.cachedTokens;
    }

    public List<CohereLogprobs> logprobs() { return logprobs; }

    public CohereBilledUnits billedUnits() { return billedUnits; }

    public Integer cachedTokens() { return cachedTokens; }

    @Override
    public Builder toBuilder() {
        return ((Builder) super.toBuilder(builder()))
                .logprobs(logprobs)
                .billedUnits(billedUnits)
                .cachedTokens(cachedTokens);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereChatResponseMetadata that
                && super.equals(that)
                && Objects.equals(logprobs, that.logprobs)
                && Objects.equals(billedUnits, that.billedUnits)
                && Objects.equals(cachedTokens, that.cachedTokens);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), logprobs, billedUnits, cachedTokens);
    }

    @Override
    public String toString() {
        return "CohereChatResponseMetadata{"
                + "id=" + quoted(id())
                + ", modelName=" + quoted(modelName())
                + ", tokenUsage=" + tokenUsage()
                + ", finishReason=" + finishReason()
                + ", logprobs=" + logprobs
                + ", billedUnits=" + billedUnits
                + ", cachedTokens=" + cachedTokens
                + '}';
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private List<CohereLogprobs> logprobs;
        private CohereBilledUnits billedUnits;
        private Integer cachedTokens;

        public Builder logprobs(List<CohereLogprobs> logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public Builder billedUnits(CohereBilledUnits billedUnits) {
            this.billedUnits = billedUnits;
            return this;
        }

        public Builder cachedTokens(Integer cachedTokens) {
            this.cachedTokens = cachedTokens;
            return this;
        }

        @Override
        public CohereChatResponseMetadata build() {
            return new CohereChatResponseMetadata(this);
        }
    }
}
