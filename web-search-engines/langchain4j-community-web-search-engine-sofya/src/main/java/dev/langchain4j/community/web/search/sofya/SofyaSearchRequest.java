package dev.langchain4j.community.web.search.sofya;

import java.util.List;

class SofyaSearchRequest {

    private final String query;
    private final Integer maxResults;
    private final String searchDepth;
    private final Boolean includeAnswer;
    private final List<String> includeDomains;
    private final List<String> excludeDomains;
    private final String topic;
    private final String freshness;

    private SofyaSearchRequest(Builder builder) {
        this.query = builder.query;
        this.maxResults = builder.maxResults;
        this.searchDepth = builder.searchDepth == null ? null : builder.searchDepth.value();
        this.includeAnswer = builder.includeAnswer;
        this.includeDomains = nullIfEmpty(builder.includeDomains);
        this.excludeDomains = nullIfEmpty(builder.excludeDomains);
        this.topic = builder.topic == null ? null : builder.topic.value();
        this.freshness = builder.freshness;
    }

    private static List<String> nullIfEmpty(List<String> list) {
        return list == null || list.isEmpty() ? null : List.copyOf(list);
    }

    static Builder builder() {
        return new Builder();
    }

    public String getQuery() {
        return query;
    }

    public Integer getMaxResults() {
        return maxResults;
    }

    public String getSearchDepth() {
        return searchDepth;
    }

    public Boolean getIncludeAnswer() {
        return includeAnswer;
    }

    public List<String> getIncludeDomains() {
        return includeDomains;
    }

    public List<String> getExcludeDomains() {
        return excludeDomains;
    }

    public String getTopic() {
        return topic;
    }

    public String getFreshness() {
        return freshness;
    }

    static class Builder {
        private String query;
        private Integer maxResults;
        private SofyaSearchDepth searchDepth;
        private Boolean includeAnswer;
        private List<String> includeDomains;
        private List<String> excludeDomains;
        private SofyaSearchTopic topic;
        private String freshness;

        Builder query(String query) {
            this.query = query;
            return this;
        }

        Builder maxResults(Integer maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        Builder searchDepth(SofyaSearchDepth searchDepth) {
            this.searchDepth = searchDepth;
            return this;
        }

        Builder includeAnswer(Boolean includeAnswer) {
            this.includeAnswer = includeAnswer;
            return this;
        }

        Builder includeDomains(List<String> includeDomains) {
            this.includeDomains = includeDomains;
            return this;
        }

        Builder excludeDomains(List<String> excludeDomains) {
            this.excludeDomains = excludeDomains;
            return this;
        }

        Builder topic(SofyaSearchTopic topic) {
            this.topic = topic;
            return this;
        }

        Builder freshness(String freshness) {
            this.freshness = freshness;
            return this;
        }

        SofyaSearchRequest build() {
            return new SofyaSearchRequest(this);
        }
    }
}
