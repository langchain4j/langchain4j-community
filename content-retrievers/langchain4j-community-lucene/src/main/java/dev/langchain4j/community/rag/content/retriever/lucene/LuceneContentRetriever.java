package dev.langchain4j.community.rag.content.retriever.lucene;

import static dev.langchain4j.community.rag.content.retriever.lucene.DirectoryFactory.tempDirectory;
import static dev.langchain4j.community.rag.content.retriever.lucene.LuceneDocumentFields.CONTENT_FIELD_NAME;
import static dev.langchain4j.community.rag.content.retriever.lucene.LuceneDocumentFields.EMBEDDING_FIELD_NAME;
import static dev.langchain4j.community.rag.content.retriever.lucene.LuceneDocumentFields.TOKEN_COUNT_FIELD_NAME;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredValue;
import org.apache.lucene.document.StoredValue.Type;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Full-text content retrieval using Apache Lucene for LangChain4J RAG.
 */
public final class LuceneContentRetriever implements ContentRetriever {

    private static final Logger log = LoggerFactory.getLogger(LuceneContentRetriever.class);

    private final Directory directory;
    private final EmbeddingModel embeddingModel;
    private final boolean onlyMatches;
    private final int maxResults;
    private final int maxTokens;
    private final double minScore;
    private final String contentFieldName;
    private final String tokenCountFieldName;
    private final String embeddingFieldName;

    /**
     * Initialize all fields, and do validation
     */
    private LuceneContentRetriever(LuceneContentRetrieverBuilder builder) {
        this.directory = getOrDefault(builder.directory, tempDirectory());
        this.embeddingModel = builder.embeddingModel;
        this.onlyMatches = builder.onlyMatches;
        this.maxResults = Math.max(0, builder.maxResults);
        this.maxTokens = Math.max(0, builder.maxTokens);
        this.minScore = Math.max(0, builder.minScore);
        this.contentFieldName = ensureNotBlank(
                getOrDefault(builder.contentFieldName, CONTENT_FIELD_NAME.fieldName()), "contentFieldName");
        this.tokenCountFieldName = ensureNotBlank(
                getOrDefault(builder.tokenCountFieldName, TOKEN_COUNT_FIELD_NAME.fieldName()), "tokenCountFieldName");
        this.embeddingFieldName = ensureNotBlank(
                getOrDefault(builder.embeddingFieldName, EMBEDDING_FIELD_NAME.fieldName()), "embeddingFieldName");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Content> retrieve(Query query) {
        String queryText = Optional.ofNullable(query).map(Query::text).orElse(null);

        int docCount = 0;
        int tokenCount = 0;
        try (DirectoryReader reader = DirectoryReader.open(directory)) {

            Embedding embedding = embedQuery(queryText);
            org.apache.lucene.search.Query luceneQuery = buildQuery(queryText, embedding);

            IndexSearcher searcher = new IndexSearcher(reader);
            TopFieldDocs topDocs = searcher.search(luceneQuery, maxResults, Sort.RELEVANCE, true);
            List<Content> hits = new ArrayList<>();
            StoredFields storedFields = reader.storedFields();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                if (scoreDoc.score < minScore) {
                    continue;
                }
                // Retrieve document contents
                Document document = storedFields.document(scoreDoc.doc);
                String content = document.get(contentFieldName);
                if (content == null || content.isBlank()) {
                    continue;
                }

                // Check if number of documents is exceeded
                docCount = docCount + 1;
                if (docCount > maxResults) {
                    break;
                }

                // Check token count
                IndexableField tokenCountField = document.getField(tokenCountFieldName);
                if (tokenCountField != null) {
                    int docTokens = tokenCountField.numericValue().intValue();
                    if (tokenCount + docTokens > maxTokens) {
                        continue;
                        // There may be smaller documents to come after this that we can accommodate
                    }
                    tokenCount = tokenCount + docTokens;
                }

                // Add all other document fields to metadata
                Metadata metadata = createTextSegmentMetadata(document);

                // Finally, add text segment to the list
                TextSegment textSegment = TextSegment.from(content, metadata);
                hits.add(Content.from(textSegment, withScore(scoreDoc)));
            }
            return hits;
        } catch (Throwable e) {
            // Catch Throwable, since Lucene can throw AssertionError
            log.error("Could not query {}", query, e);
            return Collections.emptyList();
        }
    }

    /**
     * Build a Lucene hybrid full-text and embedding vector query.
     *
     * @param query     User prompt
     * @param embedding User prompt embedding vector, or null if not available
     * @return Lucene query
     * @throws ParseException When the query cannot be parsed into terms
     */
    private org.apache.lucene.search.Query buildQuery(String query, Embedding embedding) {
        Builder builder = new BooleanQuery.Builder();

        if (query != null && !query.isBlank()) {
            try {
                QueryParser parser = new QueryParser(contentFieldName, new StandardAnalyzer());
                org.apache.lucene.search.Query fullTextQuery = parser.parse(query);
                builder.add(fullTextQuery, Occur.SHOULD);
            } catch (ParseException e) {
                log.warn("Could not create query {}", query, e);
            }
        } else {
            log.debug("Query text not provided");
        }

        if (embedding != null && embedding.vector().length > 0) {
            org.apache.lucene.search.Query vectorQuery =
                    new KnnFloatVectorQuery(embeddingFieldName, embedding.vector(), maxResults);
            builder.add(vectorQuery, Occur.SHOULD);
        } else {
            log.debug("Query embedding vector not provided, query: {}", query);
        }

        if (!onlyMatches) {
            builder.add(new MatchAllDocsQuery(), Occur.SHOULD);
            log.debug("Returning all documents, not just matches, query: {}", query);
        }

        return builder.build();
    }

    /**
     * Map Lucene document fields as metadata, preserving types as much as possible.
     *
     * @param document Lucene document
     * @return Text segment metadata
     */
    private Metadata createTextSegmentMetadata(Document document) {
        Metadata metadata = new Metadata();
        for (IndexableField field : document) {
            String fieldName = field.name();
            if (contentFieldName.equals(fieldName)) {
                continue;
            }

            StoredValue storedValue = field.storedValue();
            Type type = storedValue.getType();
            switch (type) {
                case INTEGER -> metadata.put(fieldName, storedValue.getIntValue());
                case LONG -> metadata.put(fieldName, storedValue.getLongValue());
                case FLOAT -> metadata.put(fieldName, storedValue.getFloatValue());
                case DOUBLE -> metadata.put(fieldName, storedValue.getDoubleValue());
                case STRING -> metadata.put(fieldName, storedValue.getStringValue());
                default -> {
                    // No-Op
                }
            }
        }
        return metadata;
    }

    private Embedding embedQuery(String queryText) {
        Embedding embedding = null;
        if (embeddingModel != null) {
            Response<Embedding> embeddingResponse = embeddingModel.embed(queryText);
            if (embeddingResponse != null) {
                embedding = embeddingResponse.content();
            }
        }
        return embedding;
    }

    /**
     * Create content metadata with hit score.
     *
     * @param scoreDoc Lucene score doc
     * @return Metadata map with score
     */
    private Map<ContentMetadata, Object> withScore(ScoreDoc scoreDoc) {
        return Map.of(ContentMetadata.SCORE, (double) scoreDoc.score);
    }

    /**
     * Instantiate a builder for `LuceneContentRetriever`.
     *
     * @return Builder for `LuceneContentRetriever`
     */
    public static LuceneContentRetrieverBuilder builder() {
        return new LuceneContentRetrieverBuilder();
    }

    /**
     * Builder for `LuceneContentRetriever`.
     */
    public static class LuceneContentRetrieverBuilder {

        private Directory directory;
        private EmbeddingModel embeddingModel;
        private boolean onlyMatches;
        private int maxResults;
        private int maxTokens;
        private double minScore;
        private String contentFieldName;
        private String tokenCountFieldName;
        private String embeddingFieldName;

        private LuceneContentRetrieverBuilder() {
            // Set defaults
            onlyMatches = true;
            maxResults = 10;
            maxTokens = Integer.MAX_VALUE;
            minScore = 0;
            contentFieldName = CONTENT_FIELD_NAME.fieldName();
            tokenCountFieldName = LuceneDocumentFields.TOKEN_COUNT_FIELD_NAME.fieldName();
            embeddingFieldName = LuceneDocumentFields.EMBEDDING_FIELD_NAME.fieldName();
        }

        /**
         * Sets the name of the content field.
         *
         * @param contentFieldName Content field name
         * @return Builder
         */
        public LuceneContentRetrieverBuilder contentFieldName(String contentFieldName) {
            this.contentFieldName = contentFieldName;
            return this;
        }

        /**
         * Sets the Lucene directory. If null, a temporary file-based directory is used.
         *
         * @param directory Lucene directory
         * @return Builder
         */
        public LuceneContentRetrieverBuilder directory(Directory directory) {
            this.directory = directory;
            return this;
        }

        /**
         * Sets the name of the embedding vector field.
         *
         * @param embeddingFieldName Embedding vector field name
         * @return Builder
         */
        public LuceneContentRetrieverBuilder embeddingFieldName(String embeddingFieldName) {
            this.embeddingFieldName = embeddingFieldName;
            return this;
        }

        /**
         * Sets the EmbeddingModel. If null, only full-text search is available, since the query is not
         * embedded.
         *
         * @param embeddingModel EmbeddingModel to embed the query
         * @return Builder
         */
        public LuceneContentRetrieverBuilder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        /**
         * Provides documents until the number of max results, even if there is no good match.
         *
         * @return Builder
         */
        public LuceneContentRetrieverBuilder matchUntilMaxResults() {
            onlyMatches = false;
            return this;
        }

        /**
         * Returns only a certain number of documents.
         *
         * @param maxResults Number of documents to return
         * @return Builder
         */
        public LuceneContentRetrieverBuilder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        /**
         * Returns documents until the maximum token limit is reached.
         *
         * @param maxTokens Maximum number of tokens
         * @return Builder
         */
        public LuceneContentRetrieverBuilder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        /**
         * Returns values above a certain score.
         *
         * @param minScore Threshold score
         * @return Builder
         */
        public LuceneContentRetrieverBuilder minScore(double minScore) {
            this.minScore = minScore;
            return this;
        }

        /**
         * Provides only documents matched to the query using full text search.
         *
         * @return Builder
         */
        public LuceneContentRetrieverBuilder onlyMatches() {
            onlyMatches = true;
            return this;
        }

        /**
         * Sets the name of the token count field.
         *
         * @param tokenCountFieldName Token count field name
         * @return Builder
         */
        public LuceneContentRetrieverBuilder tokenCountFieldName(String tokenCountFieldName) {
            this.tokenCountFieldName = tokenCountFieldName;
            return this;
        }

        /**
         * Build an instance of `LuceneContentRetriever` using internal builder field values.
         *
         * @return New instance of `LuceneContentRetriever`
         */
        public LuceneContentRetriever build() {
            if (directory == null) {
                directory = tempDirectory();
            }
            return new LuceneContentRetriever(this);
        }
    }
}
