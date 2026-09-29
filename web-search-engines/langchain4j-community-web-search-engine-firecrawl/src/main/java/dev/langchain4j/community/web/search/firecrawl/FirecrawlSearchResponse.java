package dev.langchain4j.community.web.search.firecrawl;

import java.util.List;

class FirecrawlSearchResponse {

    private Boolean success;
    private String error;
    private Data data;

    FirecrawlSearchResponse() {}

    public Boolean getSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }

    public Data getData() {
        return data;
    }

    static class Data {

        private List<FirecrawlSearchResult> web;

        Data() {}

        public List<FirecrawlSearchResult> getWeb() {
            return web;
        }
    }
}
