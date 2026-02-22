package dev.langchain4j.community.model.client.chat.message;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereMessage {

    private CohereRole role;
    private List<CohereMessageContent> content;

    public CohereMessage() {}

    public CohereMessage(CohereRole role, List<CohereMessageContent> content) {
        this.role = role;
        this.content = content;
    }

    public int hashCode() {
        return Objects.hash(role, content);
    }

    public CohereRole getRole() {
        return role;
    }

    public List<CohereMessageContent> getContent() {
        return content;
    }

    public boolean equals(Object o) {
        if (o == null || !(o instanceof CohereMessage)) return false;
        CohereMessage that = (CohereMessage) o;
        return role == that.role && content.equals(that.content);
    }

    public String toString() {
        return "CohereMessage{role=" + role + ", content=" + content + "}";
    }
}
