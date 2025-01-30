package dev.langchain4j.rag.content.retriever.lucene;

public enum LuceneFields {
    ID_FIELD_NAME("id"),
    CONTENT_FIELD_NAME("content"),
    TOKEN_COUNT_FIELD_NAME("estimated-token-count"),
    EMBEDDING_FIELD_NAME("embedding");

    private final String fieldName;

    private LuceneFields(String fieldName) {
        this.fieldName = fieldName;
    }

    public String fieldName() {
        return fieldName;
    }
}
