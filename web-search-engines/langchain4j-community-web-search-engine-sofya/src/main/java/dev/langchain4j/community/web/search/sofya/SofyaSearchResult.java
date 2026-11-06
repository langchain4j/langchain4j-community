package dev.langchain4j.community.web.search.sofya;

class SofyaSearchResult {

    private String title;
    private String url;
    private String content;
    private String description;
    private Boolean fetched;
    private String publishedDate;

    SofyaSearchResult() {}

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getContent() {
        return content;
    }

    public String getDescription() {
        return description;
    }

    public Boolean getFetched() {
        return fetched;
    }

    public String getPublishedDate() {
        return publishedDate;
    }
}
