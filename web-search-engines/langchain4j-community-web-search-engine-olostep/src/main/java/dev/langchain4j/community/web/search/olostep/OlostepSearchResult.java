package dev.langchain4j.community.web.search.olostep;

class OlostepSearchResult {

    private String url;
    private String title;
    private String description;
    private String markdownContent;

    OlostepSearchResult() {}

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getMarkdownContent() {
        return markdownContent;
    }
}
