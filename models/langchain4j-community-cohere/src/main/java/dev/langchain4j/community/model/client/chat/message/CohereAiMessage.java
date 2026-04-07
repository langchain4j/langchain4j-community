package dev.langchain4j.community.model.client.chat.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.community.model.client.chat.content.CohereContent;
import dev.langchain4j.community.model.client.chat.tool.CohereToolCall;

import java.util.List;
import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.community.model.client.chat.message.CohereRole.ASSISTANT;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereAiMessage implements CohereMessage {

    private final List<CohereContent> content;
    private final List<CohereToolCall> toolCalls;

    private CohereAiMessage(Builder builder) {
        this.content = builder.content;
        this.toolCalls = builder.toolCalls;
    }

    @Override
    public CohereRole role() { return ASSISTANT; }

    public List<CohereContent> content() { return content; }

    public List<CohereToolCall> toolCalls() { return toolCalls; }

    public static Builder builder() { return new Builder(); }

    public static CohereAiMessage from(String text) {
        return builder()
                .content(singletonList(CohereContent.text(text)))
                .build();
    }

    public static CohereAiMessage from(CohereToolCall... toolCalls) {
        return from(asList(toolCalls));
    }

    public static CohereAiMessage from(List<CohereToolCall> toolCalls) {
        return builder()
                .toolCalls(toolCalls)
                .build();
    }

    @Override
    public String toString() {
        return "CohereAiMessage{"
                + "content=" + content
                + ", toolCalls=" + toolCalls
                + '}';
    }

    @Override
    public int hashCode() { return Objects.hash(content, toolCalls); }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereAiMessage that && equalsTo(that);
    }

    private boolean equalsTo(CohereAiMessage that) {
        return Objects.equals(content, that.content)
                && Objects.equals(toolCalls, that.toolCalls);
    }


    public static class Builder {

        private List<CohereContent> content;
        private List<CohereToolCall> toolCalls;

        public Builder content(CohereContent... content) {
            return content(asList(content));
        }

        public Builder content(List<CohereContent> content) {
            this.content = content;
            return this;
        }

        public Builder toolCalls(CohereToolCall... toolCalls) {
            return toolCalls(asList(toolCalls));
        }

        public Builder toolCalls(List<CohereToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }

        public CohereAiMessage build() { return new CohereAiMessage(this); }
    }
}
