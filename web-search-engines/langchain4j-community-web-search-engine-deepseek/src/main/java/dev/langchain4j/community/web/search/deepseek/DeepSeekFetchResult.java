package dev.langchain4j.community.web.search.deepseek;

/** Result of fetching a web page with {@link DeepSeekWebFetchEngine}. */
public class DeepSeekFetchResult {

    private final String title;
    private final String content;

    public DeepSeekFetchResult(String title, String content) {
        this.title = title;
        this.content = content;
    }

    /** Returns the page title, or an empty string when the page has none. */
    public String title() {
        return title;
    }

    /** Returns the page content converted to Markdown. */
    public String content() {
        return content;
    }
}
