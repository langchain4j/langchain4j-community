package dev.langchain4j.community.model.client.chat.message.content;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static dev.langchain4j.community.model.client.chat.message.content.CohereContentType.IMAGE_URL;

@JsonInclude(NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereImageContent extends CohereMessageContent {

    public CohereImageContent(CohereImageUrl imageUrl) {
        super();
        this.type = IMAGE_URL;
        this.imageUrl = imageUrl;
    }

    @Override
    public String toString() {
        return "CohereImageContent{ "
                + "imageUrl = " + imageUrl
                + " }";
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), imageUrl);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereImageContent other && equalsTo(other);
    }

    private boolean equalsTo(CohereImageContent that) {
        return Objects.equals(imageUrl, that.imageUrl);
    }
}
