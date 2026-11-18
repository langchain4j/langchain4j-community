package dev.langchain4j.community.web.search.olostep;

import java.util.List;

class OlostepSearchRequest {

    private final String query;
    private final Integer limit;
    private final List<String> includeDomains;
    private final List<String> excludeDomains;
    private final ScrapeOptions scrapeOptions;

    private OlostepSearchRequest(Builder builder) {
        this.query = builder.query;
        this.limit = builder.limit;
        this.includeDomains = builder.includeDomains;
        this.excludeDomains = builder.excludeDomains;
        this.scrapeOptions = Boolean.TRUE.equals(builder.scrapeContent) ? ScrapeOptions.MARKDOWN : null;
    }

    static Builder builder() {
        return new Builder();
    }

    public String getQuery() {
        return query;
    }

    public Integer getLimit() {
        return limit;
    }

    public List<String> getIncludeDomains() {
        return includeDomains;
    }

    public List<String> getExcludeDomains() {
        return excludeDomains;
    }

    public ScrapeOptions getScrapeOptions() {
        return scrapeOptions;
    }

    static class ScrapeOptions {

        static final ScrapeOptions MARKDOWN = new ScrapeOptions(List.of("markdown"));

        private final List<String> formats;

        ScrapeOptions(List<String> formats) {
            this.formats = formats;
        }

        public List<String> getFormats() {
            return formats;
        }
    }

    static class Builder {
        private String query;
        private Integer limit;
        private List<String> includeDomains;
        private List<String> excludeDomains;
        private Boolean scrapeContent;

        Builder query(String query) {
            this.query = query;
            return this;
        }

        Builder limit(Integer limit) {
            this.limit = limit;
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

        Builder scrapeContent(Boolean scrapeContent) {
            this.scrapeContent = scrapeContent;
            return this;
        }

        OlostepSearchRequest build() {
            return new OlostepSearchRequest(this);
        }
    }
}
