package dev.langchain4j.community.model.client.chat.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.community.model.client.chat.content.CohereContent;

import java.util.List;
import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.community.model.client.chat.message.CohereRole.USER;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereUserMessage implements CohereMessage {

    private final List<CohereContent> content;

    public CohereUserMessage(List<CohereContent> content) {
        this.content = content;
    }

    @Override
    public CohereRole role() { return USER; }

    public static CohereUserMessage from(String text) {
        return new CohereUserMessage(singletonList(CohereContent.text(text)));
    }

    public static CohereUserMessage from(CohereContent... content) {
        return new CohereUserMessage(asList(content));
    }

    public static CohereUserMessage from(List<CohereContent> content) {
        return new CohereUserMessage(content);
    }

    @Override
    public String toString() {
        return "CohereUserMessage{"
                + "content=" + content
                + '}';
    }

    @Override
    public int hashCode() { return Objects.hash(content); }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereUserMessage that
                && Objects.equals(content, that.content);
    }
}
