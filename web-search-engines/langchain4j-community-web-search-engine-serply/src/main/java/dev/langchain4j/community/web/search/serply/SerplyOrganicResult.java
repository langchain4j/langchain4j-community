package dev.langchain4j.community.web.search.serply;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
class SerplyOrganicResult {

    private String title;
    private String description;
    private String link;
    private Integer position;

    /**
     * Serply's rank of the result on the underlying results page. Sent alongside
     * {@code position}, and the only rank present on some results, hence the fallback in
     * {@link SerplyWebSearchEngine}. The key is camel-cased in the API, unlike the rest.
     */
    @JsonProperty("realPosition")
    private Integer realPosition;

    public SerplyOrganicResult() {}

    public String getTitle() {
        return this.title;
    }

    public String getDescription() {
        return this.description;
    }

    public String getLink() {
        return this.link;
    }

    public Integer getPosition() {
        return this.position;
    }

    public Integer getRealPosition() {
        return this.realPosition;
    }
}
