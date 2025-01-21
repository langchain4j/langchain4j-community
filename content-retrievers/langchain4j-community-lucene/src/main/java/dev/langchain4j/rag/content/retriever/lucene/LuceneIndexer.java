package dev.langchain4j.rag.content.retriever.lucene;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;
import dev.langchain4j.data.segment.TextSegment;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.IntField;
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
public final class LuceneIndexer {

    static final String CONTENT_FIELD_NAME = "content";
    static final String TOKEN_COUNT_FIELD_NAME = "token-count";

    private static final Logger log = LoggerFactory.getLogger(LuceneIndexer.class);

    private final Directory directory;
    private final Encoding encoding;

    /**
     * Instantiate a new indexer to add content to an index based on a Lucene directory.
     *
     * @param directory Lucene directory
     */
    public LuceneIndexer(Directory directory) {
        this.directory = ensureNotNull(directory, "directory");
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        encoding = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    /**
     * Add content to a Lucene index, including segment including metadata and token count. <br/>
     * IMPORTANT: Token counts are approximate, and do not include metadata.
     *
     * @param content Text segment including metadata
     */
    public void addContent(TextSegment content) {
        if (content == null) {
            return;
        }

        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        try (IndexWriter writer = new IndexWriter(directory, config); ) {
            String text = content.text();
            int tokens = encoding.countTokens(text);
            Document doc = new Document();
            doc.add(new TextField(CONTENT_FIELD_NAME, text, Field.Store.YES));
            doc.add(new IntField(TOKEN_COUNT_FIELD_NAME, tokens, Field.Store.YES));
            Map<String, Object> metadataMap = content.metadata().toMap();
            if (metadataMap != null) {
                for (Entry<String, Object> entry : metadataMap.entrySet()) {
                    doc.add(toField(entry));
                }
            }
            writer.addDocument(doc);
        } catch (IOException e) {
            log.error(String.format("Could not write content%n%s", content), e);
        }
    }

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
