package test.dev.langchain4j.rag.content.retriever;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.lucene.DirectoryFactory;
import dev.langchain4j.rag.content.retriever.lucene.LuceneContentRetriever;
import dev.langchain4j.rag.content.retriever.lucene.LuceneEmbeddingStore;
import dev.langchain4j.rag.query.Query;
import java.util.List;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class IndexerTest {

    private static final TextSegment textSegment =
            TextSegment.from("Lucene is a powerful search library.", metadataName("doc1"));
    private static final Query query = Query.from("Give me information on the lucine search library");

    private static Metadata metadataName(String name) {
        Metadata metadata = new Metadata();
        metadata.put("name", name);
        return metadata;
    }

    private Directory directory;
    private LuceneEmbeddingStore indexer;
    private LuceneContentRetriever contentRetriever;

    @Test
    public void addEmbeddingTextSegment() {

        indexer = LuceneEmbeddingStore.builder().directory(directory).build();

        indexer.add(null, textSegment);

        List<Content> results = contentRetriever.retrieve(query);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).textSegment().text()).isEqualTo(textSegment.text());
    }

    @BeforeEach
    public void setUp() {
        directory = DirectoryFactory.tempDirectory();

        contentRetriever = LuceneContentRetriever.builder().directory(directory).build();
    }

    @AfterEach
    public void tearDown() throws Exception {
        directory.close();
    }
}
