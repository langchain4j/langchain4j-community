package dev.langchain4j.community.model.dashscope;

import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.PartialToolCall;
import java.util.List;
import java.util.Objects;

public class QwenPartialResponse {

    private final String delta;
    private final PartialThinking partialThinking;
    private final List<PartialToolCall> partialToolCalls;
    private final List<CompleteToolCall> completeToolCalls;

    private QwenPartialResponse(Builder builder) {
        this.delta = builder.delta;
        this.partialThinking = builder.partialThinking;
        this.partialToolCalls = builder.partialToolCalls;
        this.completeToolCalls = builder.completeToolCalls;
    }

    public String delta() {
        return delta;
    }

    public PartialThinking partialThinking() {
        return partialThinking;
    }

    public List<PartialToolCall> partialToolCalls() {
        return partialToolCalls;
    }

    public List<CompleteToolCall> completeToolCalls() {
        return completeToolCalls;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        QwenPartialResponse that = (QwenPartialResponse) o;
        return Objects.equals(delta, that.delta)
                && Objects.equals(partialThinking, that.partialThinking)
                && Objects.equals(partialToolCalls, that.partialToolCalls)
                && Objects.equals(completeToolCalls, that.completeToolCalls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delta, partialThinking, partialToolCalls, completeToolCalls);
    }

    @Override
    public String toString() {
        return "QwenPartialResponse{" + "delta='"
                + delta + '\'' + ", partialThinking="
                + partialThinking + ", partialToolCalls="
                + partialToolCalls + ", completeToolCalls="
                + completeToolCalls + '}';
    }

    static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String delta;
        private PartialThinking partialThinking;
        private List<PartialToolCall> partialToolCalls;
        private List<CompleteToolCall> completeToolCalls;

        public Builder delta(String delta) {
            this.delta = delta;
            return this;
        }

        public Builder partialThinking(PartialThinking partialThinking) {
            this.partialThinking = partialThinking;
            return this;
        }

        public Builder partialToolCalls(List<PartialToolCall> partialToolCalls) {
            this.partialToolCalls = partialToolCalls;
            return this;
        }

        public Builder completeToolCalls(List<CompleteToolCall> completeToolCalls) {
            this.completeToolCalls = completeToolCalls;
            return this;
        }

        public QwenPartialResponse build() {
            return new QwenPartialResponse(this);
        }
    }
}
