package dev.langchain4j.rag.content.retriever.lucene;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Full-text content retrieval using Apache Lucene for LangChain4J RAG.
 */
public final class LuceneContentRetriever implements ContentRetriever {

    /**
     * Builder for `LuceneContentRetriever`.
     */
    public static class LuceneContentRetrieverBuilder {

        private Directory directory;
        private boolean onlyMatches;
        private int topNMatches;
        private int maxTokenCount;
        private String contentFieldName;
        private String tokenCountFieldName;

        private LuceneContentRetrieverBuilder() {
            // Set defaults
            onlyMatches = true;
            topNMatches = 10;
            maxTokenCount = Integer.MAX_VALUE;
            contentFieldName = LuceneIndexer.CONTENT_FIELD_NAME;
            tokenCountFieldName = LuceneIndexer.TOKEN_COUNT_FIELD_NAME;
        }

        /**
         * Build an instance of `LuceneContentRetriever` using internal builder field values.
         *
         * @return New instance of `LuceneContentRetriever`
         */
        public LuceneContentRetriever build() {
            if (directory == null) {
                directory = DirectoryFactory.tempDirectory();
            }
            return new LuceneContentRetriever(
                    directory, onlyMatches, topNMatches, maxTokenCount, contentFieldName, tokenCountFieldName);
        }

        /**
         * Sets the name of the content field.
         *
         * @param contentFieldName Content field name
         * @return Builder
         */
        public LuceneContentRetrieverBuilder contentFieldName(String contentFieldName) {
            if (contentFieldName == null || contentFieldName.isBlank()) {
                this.contentFieldName = LuceneIndexer.CONTENT_FIELD_NAME;
            } else {
                this.contentFieldName = contentFieldName;
            }

            return this;
        }

        /**
         * Sets the Lucene directory. If null, a temporary file-based directory is used.
         *
         * @param directory Lucene directory
         * @return Builder
         */
        public LuceneContentRetrieverBuilder directory(Directory directory) {
            // Can be null
            this.directory = directory;
            return this;
        }

        /**
         * Provides documents until the top N, even if there is no good match.
         *
         * @return Builder
         */
        public LuceneContentRetrieverBuilder matchUntilTopN() {
            onlyMatches = false;
            return this;
        }

        /**
         * Returns documents until the maximum token limit is reached.
         *
         * @param maxTokenCount Maximum number of tokens
         * @return Builder
         */
        public LuceneContentRetrieverBuilder maxTokenCount(int maxTokenCount) {
            if (maxTokenCount >= 0) {
                this.maxTokenCount = maxTokenCount;
            }
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
            if (tokenCountFieldName == null || tokenCountFieldName.isBlank()) {
                this.tokenCountFieldName = LuceneIndexer.TOKEN_COUNT_FIELD_NAME;
            } else {
                this.tokenCountFieldName = tokenCountFieldName;
            }

            return this;
        }

        /**
         * Returns only a certain number of documents.
         *
         * @param topNMatches Number of documents to return
         * @return Builder
         */
        public LuceneContentRetrieverBuilder topNMatches(int topNMatches) {
            if (topNMatches >= 0) {
                this.topNMatches = topNMatches;
            }
            return this;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(LuceneContentRetriever.class);

    /**
     * Instantiate a builder for `LuceneContentRetriever`.
     *
     * @return Builder for `LuceneContentRetriever`
     */
    public static LuceneContentRetrieverBuilder builder() {
        return new LuceneContentRetrieverBuilder();
    }

    private final Directory directory;
    private final boolean onlyMatches;
    private final int topNMatches;
    private final int maxTokenCount;
    private final String contentFieldName;
    private final String tokenCountFieldName;

    /**
     * Initialize all fields, and do one more round of validation (even though the builder has
     * validated the fields).
     *
     * @param directory Lucene directory
     * @param onlyMatches Whether to only consider matching documents
     * @param topNMatches Return only the first n matches
     * @param maxTokenCount Return until a maximum token count
     * @param contentFieldName Name of the Lucene field with the text
     * @param tokenCountFieldName Name of the Lucene field with token counts
     */
    private LuceneContentRetriever(
            Directory directory,
            boolean onlyMatches,
            int topNMatches,
            int maxTokenCount,
            String contentFieldName,
            String tokenCountFieldName) {
        this.directory = ensureNotNull(directory, "directory");
        this.onlyMatches = onlyMatches;
        this.topNMatches = Math.max(0, topNMatches);
        this.maxTokenCount = Math.max(0, maxTokenCount);
        this.contentFieldName = ensureNotBlank(contentFieldName, "contentFieldName");
        this.tokenCountFieldName = ensureNotBlank(tokenCountFieldName, "tokenCountFieldName");
    }

    /** {@inheritDoc} */
    @Override
    public List<Content> retrieve(dev.langchain4j.rag.query.Query query) {
        if (query == null) {
            return Collections.emptyList();
        }

        int docCount = 0;
        int tokenCount = 0;
        try (DirectoryReader reader = DirectoryReader.open(directory)) {

            Query luceneQuery = buildQuery(query.text());

            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs topDocs = searcher.search(luceneQuery, topNMatches, Sort.RELEVANCE);
            List<Content> hits = new ArrayList<>();
            StoredFields storedFields = reader.storedFields();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                // Retrieve document contents
                Document document = storedFields.document(scoreDoc.doc);
                String content = document.get(contentFieldName);
                if (content == null || content.isBlank()) {
                    continue;
                }

                // Check if number of documents is exceeded
                docCount = docCount + 1;
                if (docCount > topNMatches) {
                    break;
                }

                // Check token count
                IndexableField tokenCountField = document.getField(tokenCountFieldName);
                if (tokenCountField != null) {
                    int docTokens = tokenCountField.numericValue().intValue();
                    if (tokenCount + docTokens > maxTokenCount) {
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
            log.error(String.format("Could not query <%s>", query), e);
            return Collections.emptyList();
        }
    }

    /**
     * Build a Lucene query. <br />
     * TODO: This may be extended in the future to allow for hybrid full text
     * and embedding vector search.
     *
     * @param query User prompt
     *
     * @return Lucene query
     *
     * @throws ParseException When the query cannot be parsed into terms
     */
    private Query buildQuery(String query) {
        Query fullTextQuery;
        try {
            QueryParser parser = new QueryParser(contentFieldName, new StandardAnalyzer());
            fullTextQuery = parser.parse(query);
        } catch (ParseException e) {
            log.warn(String.format("Could not create query <%s>", query), e);
            return new MatchAllDocsQuery();
        }

        if (onlyMatches) {
            return fullTextQuery;
        }

        BooleanQuery combinedQuery = new BooleanQuery.Builder()
                .add(fullTextQuery, Occur.SHOULD)
                .add(new MatchAllDocsQuery(), Occur.SHOULD)
                .build();
        return combinedQuery;
    }

    /**
     * Map Lucene document fields as metadata, preserving types as much as possible.
     *
     * @param document Lucene document
     *
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
                case INTEGER:
                    metadata.put(fieldName, storedValue.getIntValue());
                    break;
                case LONG:
                    metadata.put(fieldName, storedValue.getLongValue());
                    break;
                case FLOAT:
                    metadata.put(fieldName, storedValue.getFloatValue());
                    break;
                case DOUBLE:
                    metadata.put(fieldName, storedValue.getDoubleValue());
                    break;
                case STRING:
                    metadata.put(fieldName, storedValue.getStringValue());
                    break;
                default:
                    // No-op
            }
        }
        return metadata;
    }

    /**
     * Obtaining the hit score is poorly defined in Lucene, so protect it with a try block.
     *
     * @param scoreDoc Lucene score doc
     *
     * @return Metadata map with score
     */
    private Map<ContentMetadata, Object> withScore(ScoreDoc scoreDoc) {
        Map<ContentMetadata, Object> contentMetadata = new HashMap<>();
        try {
            contentMetadata.put(ContentMetadata.SCORE, (float) ((FieldDoc) scoreDoc).fields[0] - 1f);
        } catch (Exception e) {
            // Ignore = No score will be added to content metadata
        }
        return contentMetadata;
    }
}
