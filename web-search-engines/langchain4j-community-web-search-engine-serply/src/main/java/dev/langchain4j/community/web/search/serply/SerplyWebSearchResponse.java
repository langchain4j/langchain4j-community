package dev.langchain4j.community.web.search.serply;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
class SerplyWebSearchResponse {

    private List<SerplyOrganicResult> results;

    /**
     * Total number of results the engine reports for the query.
     * Serply includes this key in every response but leaves it {@code null} for regular
     * web searches, so callers must be prepared for it to be absent.
     */
    private Long total;

    /**
     * Serply's "People Also Ask" data.
     * Populated only when present on the underlying search results page, so it is
     * surfaced here as raw, untyped data rather than mapped into dedicated fields.
     */
    private List<Object> relatedQuestions;

    public SerplyWebSearchResponse() {}

    public List<SerplyOrganicResult> getResults() {
        return this.results;
    }

    public Long getTotal() {
        return this.total;
    }

    public List<Object> getRelatedQuestions() {
        return this.relatedQuestions;
    }
}
