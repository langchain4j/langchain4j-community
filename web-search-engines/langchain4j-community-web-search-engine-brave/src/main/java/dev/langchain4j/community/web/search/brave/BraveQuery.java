package dev.langchain4j.community.web.search.brave;

/**
 * Represents the {@code query} section of the Brave web search response,
 * which echoes the query and provides pagination hints.
 */
class BraveQuery {

    private Boolean moreResultsAvailable;

    public BraveQuery() {}

    public Boolean getMoreResultsAvailable() {
        return this.moreResultsAvailable;
    }
}
