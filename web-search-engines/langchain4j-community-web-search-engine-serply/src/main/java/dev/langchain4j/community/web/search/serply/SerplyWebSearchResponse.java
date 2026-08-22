package dev.langchain4j.community.web.search.serply;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;
import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
class SerplyWebSearchResponse {

    private List<OrganicResult> results;
    private Long total;

    /**
     * Serply's "People Also Ask" data.
     * Populated only when present on the underlying search results page, so it is
     * surfaced here as raw, untyped data rather than mapped into dedicated fields.
     */
    private List<Object> relatedQuestions;

    public SerplyWebSearchResponse() {}

    public List<OrganicResult> getResults() {
        return this.results;
    }

    public Long getTotal() {
        return this.total;
    }

    public List<Object> getRelatedQuestions() {
        return this.relatedQuestions;
    }
}

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
class OrganicResult {

    private String title;
    private String description;
    private String link;
    private Integer position;
    private Map<String, Object> metadata;

    public OrganicResult() {}

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

    public Map<String, Object> getMetadata() {
        return this.metadata;
    }
}
