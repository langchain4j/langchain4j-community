package dev.langchain4j.community.web.search.sofya;

import java.util.List;

class SofyaSearchResponse {

    private String query;
    private String answer;
    private List<SofyaSearchResult> results;
    private String alteredQuery;
    private String relaxedQuery;

    SofyaSearchResponse() {}

    public String getQuery() {
        return query;
    }

    public String getAnswer() {
        return answer;
    }

    public List<SofyaSearchResult> getResults() {
        return results;
    }

    public String getAlteredQuery() {
        return alteredQuery;
    }

    public String getRelaxedQuery() {
        return relaxedQuery;
    }
}
