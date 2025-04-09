package dev.langchain4j.community.rag.content.retriever.lucene;

import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.Utils.randomUUID;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lucene indexer for LangChain4J content (in the form of `TextSegment`).
 */
public final class LuceneEmbeddingStore implements EmbeddingStore<TextSegment> {

    /**
     * Builder for `LuceneEmbeddingStore`.
     */
    public static class LuceneEmbeddingStoreBuilder {

        private Directory directory;

        private LuceneEmbeddingStoreBuilder() {
            // Set defaults
        }

        /**
         * Build an instance of `LuceneContentRetriever` using internal builder field values.
         *
         * @return New instance of `LuceneContentRetriever`
         */
        public LuceneEmbeddingStore build() {
            if (directory == null) {
                directory = DirectoryFactory.tempDirectory();
            }
            return new LuceneEmbeddingStore(directory);
        }

        /**
         * Sets the Lucene directory. If null, a temporary file-based directory is used.
         *
         * @param directory Lucene directory
         * @return Builder
         */
        public LuceneEmbeddingStoreBuilder directory(Directory directory) {
            // Can be null
            this.directory = directory;
            return this;
        }
    }

    private static final String ID_FIELD_NAME = LuceneDocumentFields.ID_FIELD_NAME.fieldName();
    private static final String CONTENT_FIELD_NAME = LuceneDocumentFields.CONTENT_FIELD_NAME.fieldName();
    private static final String TOKEN_COUNT_FIELD_NAME = LuceneDocumentFields.TOKEN_COUNT_FIELD_NAME.fieldName();
    private static final String EMBEDDING_FIELD_NAME = LuceneDocumentFields.EMBEDDING_FIELD_NAME.fieldName();

    private static final Logger log = LoggerFactory.getLogger(LuceneEmbeddingStore.class);

    /**
     * Instantiate a builder for `LuceneEmbeddingStore`.
     *
     * @return Builder for `LuceneEmbeddingStore`
     */
    public static LuceneEmbeddingStoreBuilder builder() {
        return new LuceneEmbeddingStoreBuilder();
    }

    private final Directory directory;
    private final Encoding encoding;

    /**
     * Instantiate a new indexer to add content to an index based on a Lucene directory.
     *
     * @param directory Lucene directory
     */
    private LuceneEmbeddingStore(Directory directory) {
        this.directory = ensureNotNull(directory, "directory");
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        encoding = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String add(Embedding embedding) {
        if (embedding == null) {
            return null;
        }
        String id = randomUUID();
        add(id, embedding, null);
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String add(Embedding embedding, TextSegment textSegment) {
        String id = randomUUID();
        add(id, embedding, textSegment);
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void add(String id, Embedding embedding) {
        if (embedding == null) {
            return;
        }
        add(id, embedding, null);
    }

    /**
     * Add content to a Lucene index, including segment, metadata and token count. Ids are generated
     * if they are null. <br>
     * IMPORTANT: Token counts are approximate, and do not include metadata.
     *
     * @param id        Content id, can be null
     * @param embedding Content embedding, can be null
     * @param content   Content, can be null
     */
    public void add(String id, Embedding embedding, TextSegment content) {
        addAll(Collections.singletonList(id), Collections.singletonList(embedding), Collections.singletonList(content));
    }

    /**
     * Generate an id, and index the content (with metadata) in Lucene.
     *
     * @param textSegment Content to index
     * @return Generated id
     */
    public String add(TextSegment textSegment) {
        if (textSegment == null) {
            return null;
        }
        String id = randomUUID();
        add(id, null, textSegment);
        return id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        if (embeddings == null || embeddings.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = generateIds(embeddings.size());
        addAll(ids, embeddings, null);
        return ids;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAll(List<String> idsArg, List<Embedding> embeddingsArg, List<TextSegment> embeddedArg) {

        int maxSize = maxSize(idsArg, embeddingsArg, embeddedArg);

        List<String> ids = ensureSize(idsArg, maxSize);
        List<Embedding> embeddings = ensureSize(embeddingsArg, maxSize);
        List<TextSegment> embedded = ensureSize(embeddedArg, maxSize);

        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < maxSize; i++) {
            // Create Lucene documents list allowing other documents to be created even if any fail
            try {
                documents.add(toDocument(ids.get(i), embeddings.get(i), embedded.get(i)));
            } catch (Exception e) {
                log.error("Could not create Lucene document", e);
            }
        }

        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            writer.addDocuments(documents);
        } catch (IOException e) {
            log.error("Could not index documents", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        if (request == null) {
            return new EmbeddingSearchResult<>(Collections.emptyList());
        }

        ContentRetriever contentRetriever = LuceneContentRetriever.builder()
                .directory(directory)
                .embeddingModel(new KnownQueryEmbeddingModel(request.queryEmbedding()))
                .maxResults(request.maxResults())
                .minScore(request.minScore())
                .directory(directory)
                .build();
        log.debug("Ignoring request filter", request.filter());

        List<EmbeddingMatch<TextSegment>> results = new ArrayList<>();
        List<Content> contents = contentRetriever.retrieve(null);
        for (Content content : contents) {
            try {
                Map<ContentMetadata, Object> metadata = content.metadata();
                Double score;
                if (metadata != null && metadata.containsKey(ContentMetadata.SCORE)) {
                    score = (Double) metadata.get(ContentMetadata.SCORE);
                } else {
                    score = Double.NaN;
                }
                // Note: Lucene does not store embeddings (KnnFloatVectorField)
                Embedding embedding = null;
                TextSegment textSegment = content.textSegment();
                String id;
                if (textSegment != null && textSegment.metadata() != null) {
                    id = textSegment.metadata().getString(LuceneDocumentFields.ID_FIELD_NAME.fieldName());
                } else {
                    log.debug("Generating new random id");
                    id = randomUUID();
                }
                EmbeddingMatch<TextSegment> result = new EmbeddingMatch<>(score, id, embedding, textSegment);
                results.add(result);
            } catch (Exception e) {
                log.error("Could not convert content to results", e);
            }
        }

        return new EmbeddingSearchResult<>(results);
    }

    /**
     * Pad a list with null values so it is a certain size. The original list is not modified, and a
     * new list is returned. This way we can avoid threading issues if the original list was provided
     * from calling code.
     *
     * @param <P>      Type of list values
     * @param provided Provided list
     * @param maxSize  Size to pad the list to
     * @return New list padded with null values
     */
    private <P> List<P> ensureSize(List<P> provided, int maxSize) {
        List<P> sizedList;
        if (isNullOrEmpty(provided)) {
            sizedList = new ArrayList<>(Collections.nCopies(maxSize, null));
        } else {
            sizedList = new ArrayList<>(provided);
        }
        int size = sizedList.size();
        if (size < maxSize) {
            sizedList.addAll(Collections.nCopies(maxSize - size, null));
        }
        return sizedList;
    }

    /**
     * Check whether a string is null or blank.
     *
     * @param text Text to check
     * @return True if text is is null or blank
     */
    private boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * Find the maximum size of lists, so they can be made the same size later.
     *
     * @param ids        List of content ids
     * @param embeddings List of content embeddings
     * @param embedded   List of content
     * @return Maximum size of any of the lists
     */
    private int maxSize(List<String> ids, List<Embedding> embeddings, List<TextSegment> embedded) {
        int maxLen = 0;
        if (!isNullOrEmpty(ids)) {
            int size = ids.size();
            if (maxLen < size) {
                maxLen = size;
            }
        }
        if (!isNullOrEmpty(embeddings)) {
            int size = embeddings.size();
            if (maxLen < size) {
                maxLen = size;
            }
        }
        if (!isNullOrEmpty(embedded)) {
            int size = embedded.size();
            if (maxLen < size) {
                maxLen = size;
            }
        }
        return maxLen;
    }

    /**
     * Convert provided id, embedding and text to a Lucene document.
     *
     * @param id        Document id, can be null
     * @param embedding Embedding, can be null
     * @param content   Text content, can be null
     * @return Lucene document
     */
    private Document toDocument(String id, Embedding embedding, TextSegment content) {
        String text;
        if (content == null) {
            text = "";
        } else {
            text = content.text();
        }
        int tokens = encoding.countTokens(text);

        Document document = new Document();
        if (isBlank(id)) {
            document.add(new StringField(ID_FIELD_NAME, randomUUID(), Store.YES));
        } else {
            document.add(new StringField(ID_FIELD_NAME, id, Store.YES));
        }
        if (!isBlank(text)) {
            document.add(new TextField(CONTENT_FIELD_NAME, text, Store.YES));
        }
        if (embedding != null) {
            float[] vector = embedding.vector();
            if (vector != null && vector.length > 0) {
                document.add(new KnnFloatVectorField(EMBEDDING_FIELD_NAME, vector));
            }
        }
        document.add(new IntField(TOKEN_COUNT_FIELD_NAME, tokens, Store.YES));

        if (content != null) {
            Map<String, Object> metadataMap = content.metadata().toMap();
            if (metadataMap != null) {
                for (Entry<String, Object> entry : metadataMap.entrySet()) {
                    document.add(toField(entry));
                }
            }
        }
        return document;
    }

    /**
     * Convert a LangChain4J metadata entry into a Lucene field, attempting to preserve the value
     * types.
     *
     * @param entry LangChain4J metadata entry
     * @return Lucene field
     */
    private Field toField(Entry<String, Object> entry) {
        String fieldName = entry.getKey();
        var fieldValue = entry.getValue();
        Field field;
        if (fieldValue instanceof String string) {
            field = new StringField(fieldName, string, Store.YES);
        } else if (fieldValue instanceof Integer number) {
            field = new IntField(fieldName, number, Store.YES);
        } else if (fieldValue instanceof Long number) {
            field = new LongField(fieldName, number, Store.YES);
        } else if (fieldValue instanceof Float number) {
            field = new FloatField(fieldName, number, Store.YES);
        } else if (fieldValue instanceof Double number) {
            field = new DoubleField(fieldName, number, Store.YES);
        } else {
            field = new StringField(fieldName, String.valueOf(fieldValue), Store.YES);
        }
        return field;
    }
}
