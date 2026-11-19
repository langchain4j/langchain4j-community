package dev.langchain4j.community.web.search.sofya;

/**
 * The kind of results the Sofya search looks for.
 */
public enum SofyaSearchTopic {

    /**
     * Regular web results. This is the API's default.
     */
    GENERAL("general"),

    /**
     * News articles.
     */
    NEWS("news");

    private final String value;

    SofyaSearchTopic(String value) {
        this.value = value;
    }

    String value() {
        return value;
    }
}
