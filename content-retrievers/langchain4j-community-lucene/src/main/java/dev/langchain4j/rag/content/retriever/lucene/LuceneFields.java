package dev.langchain4j.rag.content.retriever.lucene;

/** Default field names used when storing data in Lucene. Used for both storage and retrieval. **/
public enum LuceneFields {
    /** Id of the Lucene document **/
    ID_FIELD_NAME("id"),
    /** Text content of the content **/
    CONTENT_FIELD_NAME("content"),
    /** Estimated token count of the content **/
    TOKEN_COUNT_FIELD_NAME("estimated-token-count"),
    /** Embedding vector of the content **/
    EMBEDDING_FIELD_NAME("embedding");

    private final String fieldName;

    private LuceneFields(String fieldName) {
        this.fieldName = fieldName;
    }

    /**
     * Field name used in the Lucene document.
     *
     * @return Field name
     */
    public String fieldName() {
        return fieldName;
    }
}
