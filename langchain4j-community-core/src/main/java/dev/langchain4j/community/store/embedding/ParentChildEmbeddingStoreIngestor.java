package dev.langchain4j.community.store.embedding;

import static java.util.stream.Collectors.toList;

import dev.langchain4j.Experimental;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.DocumentTransformer;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.segment.TextSegmentTransformer;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.spi.data.document.splitter.DocumentSplitterFactory;
import dev.langchain4j.spi.model.embedding.EmbeddingModelFactory;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.IngestionResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An extension of {@link EmbeddingStoreIngestor} that introduces hierarchical processing of documents
 * by supporting an additional splitting and transformation step for child segments.
 * <p>
 * This ingestor first applies a {@link DocumentSplitter} and a {@link TextSegmentTransformer} to create
 * and process parent segments. It then uses a secondary {@link DocumentSplitter} (via
 * {@link #documentChildSplitter}) and an optional {@link TextSegmentTransformer} (via
 * {@link #childTextSegmentTransformer}) to further split and process those parent segments into child segments.
 * </p>
 * <p>
 * This parent-child pattern is useful when the document structure requires multi-level granularity for
 * embedding generation and storage—for example, splitting chapters into paragraphs and then embedding
 * both levels separately.
 * </p>
 *
 * @see EmbeddingStoreIngestor
 * @since 1.1.0-beta7
 */
@Experimental
public class ParentChildEmbeddingStoreIngestor extends EmbeddingStoreIngestor {

    private static final Logger log = LoggerFactory.getLogger(ParentChildEmbeddingStoreIngestor.class);
    private final DocumentTransformer documentTransformer;
    private final DocumentSplitter documentSplitter;
    protected TextSegmentTransformer textSegmentTransformer;
    protected TextSegmentTransformer childTextSegmentTransformer;
    private final EmbeddingModel embeddingModel;
    protected EmbeddingStore<TextSegment> embeddingStore;

    private final DocumentSplitter documentChildSplitter;

    /**
     * Create an instance of ParentChildEmbeddingStoreIngestor, which processes documents through a pipeline
     * that includes transformation, hierarchical splitting into parent and child segments, and embedding generation
     * for each segment. The resulting embeddings can then be stored using the specified {@link EmbeddingStore}.
     *
     * @param documentTransformer The {@link DocumentTransformer} to preprocess or normalize documents before splitting.
     * @param documentSplitter The {@link DocumentSplitter} used to split documents into higher-level (parent) segments.
     * @param textSegmentTransformer The {@link TextSegmentTransformer} applied to each parent segment before embedding.
     * @param childTextSegmentTransformer The {@link TextSegmentTransformer} applied to child segments derived from parents.
     * @param embeddingModel The {@link EmbeddingModel} used to generate vector embeddings for both parent and child segments.
     * @param embeddingStore The {@link EmbeddingStore} used to persist the generated embeddings.
     * @param documentChildSplitter The {@link DocumentSplitter} responsible for generating child segments from parent segments.
     */
    public ParentChildEmbeddingStoreIngestor(
            final DocumentTransformer documentTransformer,
            final DocumentSplitter documentSplitter,
            final TextSegmentTransformer textSegmentTransformer,
            TextSegmentTransformer childTextSegmentTransformer,
            final EmbeddingModel embeddingModel,
            final EmbeddingStore<TextSegment> embeddingStore,
            DocumentSplitter documentChildSplitter) {
        super(documentTransformer, documentSplitter, textSegmentTransformer, embeddingModel, embeddingStore);
        this.documentTransformer = documentTransformer;
        this.documentSplitter = documentSplitter;
        this.textSegmentTransformer = textSegmentTransformer;
        this.childTextSegmentTransformer = childTextSegmentTransformer;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.documentChildSplitter = documentChildSplitter;
    }

    @Override
    public IngestionResult ingest(List<Document> documents) {
        log.debug("Starting to ingest {} documents", documents.size());

        if (documentTransformer != null) {
            documents = documentTransformer.transformAll(documents);
            log.debug("Documents were transformed into {} documents", documents.size());
        }
        List<TextSegment> segments;
        if (documentSplitter != null) {
            segments = documentSplitter.splitAll(documents);

            log.debug("Documents were split into {} text segments", segments.size());
        } else {
            segments = documents.stream().map(Document::toTextSegment).collect(toList());
        }
        if (textSegmentTransformer != null) {
            segments = textSegmentTransformer.transformAll(segments);
            log.debug("Text segments were transformed into {} text segments", documents.size());
        }

        if (documentChildSplitter != null) {
            TokenUsage totalUsage = new TokenUsage();
            for (TextSegment segment : segments) {
                // Convert back to Document to apply DocumentSplitter
                Document parentDoc = Document.from(segment.text(), segment.metadata());
                List<TextSegment> childSegments = documentChildSplitter.split(parentDoc).stream()
                        .map(i -> {
                            assert childTextSegmentTransformer != null;
                            return childTextSegmentTransformer.transform(i);
                        })
                        .toList();

                Response<List<Embedding>> embeddingsResponse = embeddingModel.embedAll(childSegments);

                embeddingStore.addAll(embeddingsResponse.content(), childSegments);

                totalUsage = totalUsage.add(embeddingsResponse.tokenUsage());
            }
            return new IngestionResult(totalUsage);
        }

        log.debug("Starting to embed {} text segments", segments.size());
        Response<List<Embedding>> embeddingsResponse = embeddingModel.embedAll(segments);
        log.debug("Finished embedding {} text segments", segments.size());

        log.debug("Starting to store {} text segments into the embedding store", segments.size());
        embeddingStore.addAll(embeddingsResponse.content(), segments);
        log.debug("Finished storing {} text segments into the embedding store", segments.size());

        return new IngestionResult(embeddingsResponse.tokenUsage());
    }

    public static ParentChildEmbeddingStoreIngestor.Builder builder() {
        return new ParentChildEmbeddingStoreIngestor.Builder();
    }

    public static class Builder<B extends ParentChildEmbeddingStoreIngestor.Builder>
            extends EmbeddingStoreIngestor.Builder {

        protected DocumentTransformer documentTransformer;
        protected DocumentSplitter documentSplitter;
        protected DocumentSplitter documentChildSplitter;
        protected TextSegmentTransformer textSegmentTransformer;
        protected TextSegmentTransformer childTextSegmentTransformer;
        protected EmbeddingModel embeddingModel;
        protected EmbeddingStore<TextSegment> embeddingStore;

        protected B self() {
            return (B) this;
        }

        /**
         * Creates a new EmbeddingStoreIngestor builder.
         */
        public Builder() {}

        /**
         * Sets the text segment transformer to be applied to child segments derived from parents. Optional.
         *
         * @param childTextSegmentTransformer the document transformer.
         * @return {@code this}
         */
        public B childTextSegmentTransformer(TextSegmentTransformer childTextSegmentTransformer) {
            this.childTextSegmentTransformer = childTextSegmentTransformer;
            return self();
        }

        /**
         * Sets the document splitter responsible for generating child segments from parent segments.
         *
         * @param documentChildSplitter the document splitter.
         * @return {@code this}
         */
        public B documentChildSplitter(DocumentSplitter documentChildSplitter) {
            this.documentChildSplitter = documentChildSplitter;
            return self();
        }

        /**
         * Sets the document transformer. Optional.
         *
         * @param documentTransformer the document transformer.
         * @return {@code this}
         */
        public B documentTransformer(DocumentTransformer documentTransformer) {
            this.documentTransformer = documentTransformer;
            return self();
        }

        /**
         * Sets the document splitter. Optional.
         * If none is specified, it tries to load one through SPI (see {@link DocumentSplitterFactory}).
         * <br>
         * {@code DocumentSplitters.recursive()} from main ({@code langchain4j}) module is a good starting point.
         *
         * @param documentSplitter the document splitter.
         * @return {@code this}
         */
        public B documentSplitter(DocumentSplitter documentSplitter) {
            this.documentSplitter = documentSplitter;
            return self();
        }

        /**
         * Sets the text segment transformer. Optional.
         *
         * @param textSegmentTransformer the text segment transformer.
         * @return {@code this}
         */
        public B textSegmentTransformer(TextSegmentTransformer textSegmentTransformer) {
            this.textSegmentTransformer = textSegmentTransformer;
            return self();
        }

        /**
         * Sets the embedding model. Mandatory.
         * If none is specified, it tries to load one through SPI (see {@link EmbeddingModelFactory}).
         *
         * @param embeddingModel the embedding model.
         * @return {@code this}
         */
        public B embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return self();
        }

        /**
         * Sets the embedding store. Mandatory.
         *
         * @param embeddingStore the embedding store.
         * @return {@code this}
         */
        public B embeddingStore(EmbeddingStore<TextSegment> embeddingStore) {
            this.embeddingStore = embeddingStore;
            return self();
        }

        /**
         * Builds the EmbeddingStoreIngestor.
         *
         * @return the EmbeddingStoreIngestor.
         */
        @Override
        public ParentChildEmbeddingStoreIngestor build() {
            return new ParentChildEmbeddingStoreIngestor(
                    documentTransformer,
                    documentSplitter,
                    textSegmentTransformer,
                    childTextSegmentTransformer,
                    embeddingModel,
                    embeddingStore,
                    documentChildSplitter);
        }
    }
}
