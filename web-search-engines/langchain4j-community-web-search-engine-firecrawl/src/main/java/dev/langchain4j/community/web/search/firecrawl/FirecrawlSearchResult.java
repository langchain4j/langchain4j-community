package dev.langchain4j.community.web.search.firecrawl;

class FirecrawlSearchResult {

    private String url;
    private String title;
    private String description;
    private Integer position;
    private String markdown;

    FirecrawlSearchResult() {}

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Integer getPosition() {
        return position;
    }

    public String getMarkdown() {
        return markdown;
    }
}
