package dev.langchain4j.community.web.search.sofya;

/**
 * How much of each result the Sofya search returns.
 */
public enum SofyaSearchDepth {

    /**
     * Title, URL and snippet only. The fastest and cheapest option, {@code content()} is always {@code null}.
     * This is the default of {@link SofyaWebSearchEngine}.
     */
    SNIPPETS("snippets"),

    /**
     * Also fetches each result page and returns its text as Markdown in {@code content()}.
     * Uses additional credits.
     */
    BASIC("basic");

    private final String value;

    SofyaSearchDepth(String value) {
        this.value = value;
    }

    String value() {
        return value;
    }
}
