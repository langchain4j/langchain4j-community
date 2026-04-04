package dev.langchain4j.community.model.client.chat.message.content;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import dev.langchain4j.data.message.ImageContent;

import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonDeserialize(builder = CohereImageUrl.Builder.class)
@JsonInclude(NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CohereImageUrl {

    private final String url;
    private final ImageContent.DetailLevel detail;

    private CohereImageUrl(Builder builder) {
        this.url = builder.url;
        this.detail = builder.detail;
    }

    public static Builder builder() { return new Builder(); }

    @Override
    public String toString() {
        return "CohereImageUrl{ "
                + "url = " + url
                + ", detail = " + detail
                + " }";
    }

    @Override
    public int hashCode() { return Objects.hash(url, detail); }

    @Override
    public boolean equals(Object o) {
        return o instanceof CohereImageUrl other && equalsTo(other);
    }

    private boolean equalsTo(CohereImageUrl that) {
        return Objects.equals(url, that.url) && Objects.equals(detail, that.detail);
    }

    @JsonPOJOBuilder(withPrefix = "")
    @JsonInclude(NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Builder {
        private String url;
        private ImageContent.DetailLevel detail;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder detail(ImageContent.DetailLevel detail) {
            this.detail = detail;
            return this;
        }

        public CohereImageUrl build() {
            return new CohereImageUrl(this);
        }
    }


}
