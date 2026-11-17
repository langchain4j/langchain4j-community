package dev.langchain4j.community.web.search.keenable;

import java.util.List;

/**
 * Body of {@code POST /v1/search} and {@code POST /v1/search/public}.
 * Properties are written in snake_case as the Keenable Search API expects; {@code null} fields are left out.
 */
class KeenableSearchRequest {

    private final String query;
    private final String mode;
    private final Integer maxResults;
    private final Integer snippetMaxLength;
    private final List<String> excludeDomains;

    private KeenableSearchRequest(Builder builder) {
        this.query = builder.query;
        this.mode = builder.mode;
        this.maxResults = builder.maxResults;
        this.snippetMaxLength = builder.snippetMaxLength;
        this.excludeDomains = builder.excludeDomains;
    }

    static Builder builder() {
        return new Builder();
    }

    public String getQuery() {
        return query;
    }

    public String getMode() {
        return mode;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public Integer getSnippetMaxLength() {
        return snippetMaxLength;
    }

    public List<String> getExcludeDomains() {
        return excludeDomains;
    }

    static class Builder {
        private String query;
        private String mode;
        private Integer maxResults;
        private Integer snippetMaxLength;
        private List<String> excludeDomains;

        Builder query(String query) {
            this.query = query;
            return this;
        }

        Builder mode(String mode) {
            this.mode = mode;
            return this;
        }

        Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        Builder snippetMaxLength(Integer snippetMaxLength) {
            this.snippetMaxLength = snippetMaxLength;
            return this;
        }

        Builder excludeDomains(List<String> excludeDomains) {
            this.excludeDomains = excludeDomains;
            return this;
        }

        KeenableSearchRequest build() {
            return new KeenableSearchRequest(this);
        }
    }
}
