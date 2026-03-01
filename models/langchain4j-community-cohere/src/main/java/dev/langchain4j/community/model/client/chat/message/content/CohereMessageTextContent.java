package dev.langchain4j.community.model.client.chat.message.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.community.model.client.chat.message.content.CohereContentType.TEXT;

@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereMessageTextContent extends CohereMessageContent {

    public CohereMessageTextContent() {}

    public CohereMessageTextContent(String text) {
        super();
        this.type = TEXT;
        this.text = text;
    }

    public String toString() {
        return "CohereMessageTextContent{text='" + text + "'}";
    }

    public boolean equals(Object o) {
        if (o == null || !(o instanceof CohereMessageTextContent)) return false;
        CohereMessageTextContent that = (CohereMessageTextContent) o;
        return this.text.equals(that.text);
    }

    public int hashCode() {
        return Objects.hash(super.hashCode(), text);
    }
}
