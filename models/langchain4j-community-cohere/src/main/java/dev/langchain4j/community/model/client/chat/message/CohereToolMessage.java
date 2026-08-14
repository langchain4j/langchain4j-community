package dev.langchain4j.community.model.client.chat.message;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.community.model.client.chat.message.CohereRole.TOOL;
import static dev.langchain4j.internal.Utils.quoted;
import static java.util.Collections.singletonList;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import dev.langchain4j.community.model.client.chat.content.CohereContent;
import java.util.List;
import java.util.Objects;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereToolMessage implements CohereMessage {

    private final String toolCallId;
    private final List<CohereContent> content;

    public CohereToolMessage(String toolCallId, List<CohereContent> content) {
        this.toolCallId = toolCallId;
        this.content = content;
    }

    @Override
    public CohereRole role() {
        return TOOL;
    }

    public static CohereToolMessage from(String toolCallId, String result) {
        return new CohereToolMessage(toolCallId, singletonList(CohereContent.text(result)));
    }

    @Override
    public String toString() {
        return "CohereToolMessage{" + "toolCallId=" + quoted(toolCallId) + ", content=" + content + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolCallId, content);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereToolMessage that
                && Objects.equals(toolCallId, that.toolCallId)
                && Objects.equals(content, that.content);
    }
}
