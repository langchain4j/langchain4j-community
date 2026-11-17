package dev.langchain4j.community.web.search.keenable;

import java.util.List;

class KeenableSearchResponse {

    private String query;
    private String mode;
    private List<KeenableSearchResult> results;

    KeenableSearchResponse() {}

    public String getQuery() {
        return query;
    }

    public String getMode() {
        return mode;
    }

    public List<KeenableSearchResult> getResults() {
        return results;
    }
}
