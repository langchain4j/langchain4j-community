package dev.langchain4j.community.model.client.chat.message;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.community.model.client.chat.message.CohereRole.SYSTEM;
import static java.util.Collections.singletonList;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.community.model.client.chat.content.CohereContent;
import java.util.List;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereSystemMessage implements CohereMessage {

    private final List<CohereContent> content;

    public CohereSystemMessage(List<CohereContent> content) {
        this.content = content;
    }

    @Override
    public CohereRole role() {
        return SYSTEM;
    }

    public static CohereMessage from(String text) {
        return new CohereSystemMessage(singletonList(CohereContent.text(text)));
    }

    @Override
    public String toString() {
        return "CohereSystemMessage{" + "content=" + content + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(content);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereSystemMessage that && Objects.equals(content, that.content);
    }
}
