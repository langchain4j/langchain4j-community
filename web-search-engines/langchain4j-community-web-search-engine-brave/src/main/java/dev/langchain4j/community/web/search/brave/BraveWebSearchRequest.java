package dev.langchain4j.community.web.search.brave;

import java.util.Map;

/**
 * Represents a request to the Brave web search endpoint.
 * The field names correspond to the Brave query parameters
 * (see <a href="https://api-dashboard.search.brave.com/app/documentation/web-search">Brave Search API documentation</a>).
 */
class BraveWebSearchRequest {

    static final String FRESHNESS = "freshness";
    static final String SPELLCHECK = "spellcheck";
    static final String EXTRA_SNIPPETS = "extra_snippets";

    private String query;
    private Integer count;
    private Integer offset;
    private String language;
    private String country;
    private String safesearch;
    private String freshness;
    private Boolean spellcheck;
    private Boolean extraSnippets;
    private Map<String, Object> additionalParameters;

    BraveWebSearchRequest(
            String query,
            Integer count,
            Integer offset,
            String language,
            String country,
            String safesearch,
            String freshness,
            Boolean spellcheck,
            Boolean extraSnippets,
            Map<String, Object> additionalParameters) {
        this.query = query;
        this.count = count;
        this.offset = offset;
        this.language = language;
        this.country = country;
        this.safesearch = safesearch;
        this.freshness = freshness;
        this.spellcheck = spellcheck;
        this.extraSnippets = extraSnippets;
        this.additionalParameters = additionalParameters;
    }

    public static BraveWebSearchRequestBuilder builder() {
        return new BraveWebSearchRequestBuilder();
    }

    public String getQuery() {
        return this.query;
    }

    public Integer getCount() {
        return this.count;
    }

    public Integer getOffset() {
        return this.offset;
    }

    public String getLanguage() {
        return this.language;
    }

    public String getCountry() {
        return this.country;
    }

    public String getSafesearch() {
        return this.safesearch;
    }

    public String getFreshness() {
        return this.freshness;
    }

    public Boolean getSpellcheck() {
        return this.spellcheck;
    }

    public Boolean getExtraSnippets() {
        return this.extraSnippets;
    }

    public Map<String, Object> getAdditionalParameters() {
        return this.additionalParameters;
    }

    public static class BraveWebSearchRequestBuilder {
        private String query;
        private Integer count;
        private Integer offset;
        private String language;
        private String country;
        private String safesearch;
        private String freshness;
        private Boolean spellcheck;
        private Boolean extraSnippets;
        private Map<String, Object> additionalParameters;

        BraveWebSearchRequestBuilder() {}

        public BraveWebSearchRequestBuilder query(String query) {
            this.query = query;
            return this;
        }

        public BraveWebSearchRequestBuilder count(Integer count) {
            this.count = count;
            return this;
        }

        public BraveWebSearchRequestBuilder offset(Integer offset) {
            this.offset = offset;
            return this;
        }

        public BraveWebSearchRequestBuilder language(String language) {
            this.language = language;
            return this;
        }

        public BraveWebSearchRequestBuilder country(String country) {
            this.country = country;
            return this;
        }

        public BraveWebSearchRequestBuilder safesearch(String safesearch) {
            this.safesearch = safesearch;
            return this;
        }

        public BraveWebSearchRequestBuilder freshness(String freshness) {
            this.freshness = freshness;
            return this;
        }

        public BraveWebSearchRequestBuilder spellcheck(Boolean spellcheck) {
            this.spellcheck = spellcheck;
            return this;
        }

        public BraveWebSearchRequestBuilder extraSnippets(Boolean extraSnippets) {
            this.extraSnippets = extraSnippets;
            return this;
        }

        public BraveWebSearchRequestBuilder additionalParameters(Map<String, Object> additionalParameters) {
            this.additionalParameters = additionalParameters;
            return this;
        }

        public BraveWebSearchRequest build() {
            return new BraveWebSearchRequest(
                    this.query,
                    this.count,
                    this.offset,
                    this.language,
                    this.country,
                    this.safesearch,
                    this.freshness,
                    this.spellcheck,
                    this.extraSnippets,
                    this.additionalParameters);
        }
    }
}
