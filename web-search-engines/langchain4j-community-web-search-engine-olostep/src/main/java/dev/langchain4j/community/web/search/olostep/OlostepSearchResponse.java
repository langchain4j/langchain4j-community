package dev.langchain4j.community.web.search.olostep;

import java.util.List;

class OlostepSearchResponse {

    private Result result;

    OlostepSearchResponse() {}

    public Result getResult() {
        return result;
    }

    static class Result {

        private List<OlostepSearchResult> links;

        Result() {}

        public List<OlostepSearchResult> getLinks() {
            return links;
        }
    }
}
