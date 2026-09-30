package dev.langchain4j.community.web.search.firecrawl;

import java.util.List;

class FirecrawlSearchRequest {

    private final String query;
    private final Integer limit;
    private final List<String> sources;
    private final String location;
    private final String tbs;
    private final ScrapeOptions scrapeOptions;
    private final String origin;

    private FirecrawlSearchRequest(Builder builder) {
        this.query = builder.query;
        this.limit = builder.limit;
        this.sources = builder.sources;
        this.location = builder.location;
        this.tbs = builder.tbs;
        this.scrapeOptions =
                Boolean.TRUE.equals(builder.scrapeContent) ? new ScrapeOptions(List.of(new Format("markdown"))) : null;
        this.origin = builder.origin;
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

    public List<String> getSources() {
        return sources;
    }

    public String getLocation() {
        return location;
    }

    public String getTbs() {
        return tbs;
    }

    public ScrapeOptions getScrapeOptions() {
        return scrapeOptions;
    }

    public String getOrigin() {
        return origin;
    }

    static class ScrapeOptions {

        private final List<Format> formats;

        ScrapeOptions(List<Format> formats) {
            this.formats = formats;
        }

        public List<Format> getFormats() {
            return formats;
        }
    }

    static class Format {

        private final String type;

        Format(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }
    }

    static class Builder {
        private String query;
        private Integer limit;
        private List<String> sources;
        private String location;
        private String tbs;
        private Boolean scrapeContent;
        private String origin;

        Builder query(String query) {
            this.query = query;
            return this;
        }

        Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        Builder sources(List<String> sources) {
            this.sources = sources;
            return this;
        }

        Builder location(String location) {
            this.location = location;
            return this;
        }

        Builder tbs(String tbs) {
            this.tbs = tbs;
            return this;
        }

        Builder scrapeContent(Boolean scrapeContent) {
            this.scrapeContent = scrapeContent;
            return this;
        }

        Builder origin(String origin) {
            this.origin = origin;
            return this;
        }

        FirecrawlSearchRequest build() {
            return new FirecrawlSearchRequest(this);
        }
    }
}
