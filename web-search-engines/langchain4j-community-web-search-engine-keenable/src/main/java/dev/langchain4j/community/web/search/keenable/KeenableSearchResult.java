package dev.langchain4j.community.web.search.keenable;

class KeenableSearchResult {

    private String title;
    private String url;
    private String snippet;
    private String publishedAt;
    private String acquiredAt;

    KeenableSearchResult() {}

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getSnippet() {
        return snippet;
    }

    public String getPublishedAt() {
        return publishedAt;
    }

    public String getAcquiredAt() {
        return acquiredAt;
    }
}
