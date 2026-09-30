package dev.langchain4j.community.web.search.brave;

/**
 * Represents the response of the Brave web search endpoint.
 */
class BraveWebSearchResponse {

    private BraveQuery query;
    private BraveWeb web;

    public BraveWebSearchResponse() {}

    public BraveQuery getQuery() {
        return this.query;
    }

    public BraveWeb getWeb() {
        return this.web;
    }
}
