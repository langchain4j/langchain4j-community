package dev.langchain4j.community.rag.content.retriever.lucene;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IndexerTest {

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
    void addAll() {

        indexer = LuceneEmbeddingStore.builder().directory(directory).build();

        indexer.addAll(null, null, Collections.singletonList(textSegment));

        List<Content> results = contentRetriever.retrieve(query);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).textSegment().metadata().getString("id")).isNotBlank();
        assertThat(results.get(0).textSegment().text()).isEqualTo(textSegment.text());
    }

    @Test
    void addAllEmbeddings() {

        indexer = LuceneEmbeddingStore.builder().directory(directory).build();

        indexer.addAll(null);

        List<Content> results = contentRetriever.retrieve(query);

        assertThat(results).hasSize(0);
    }

    @Test
    void addEmbedding() {

        indexer = LuceneEmbeddingStore.builder().directory(directory).build();

        indexer.add((Embedding) null);

        List<Content> results = contentRetriever.retrieve(query);

        assertThat(results).isEmpty();
    }

    @Test
    void addEmbeddingTextSegment() {

        indexer = LuceneEmbeddingStore.builder().directory(directory).build();

        indexer.add(null, textSegment);

        List<Content> results = contentRetriever.retrieve(query);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).textSegment().metadata().getString("id")).isNotBlank();
        assertThat(results.get(0).textSegment().text()).isEqualTo(textSegment.text());
    }

    @Test
    void addStringEmbedding() {

        indexer = LuceneEmbeddingStore.builder().directory(directory).build();

        indexer.add("id", null);

        List<Content> results = contentRetriever.retrieve(query);

        assertThat(results).isEmpty();
    }

    @Test
    void addStringEmbeddingTextSegment() {

        indexer = LuceneEmbeddingStore.builder().directory(directory).build();

        indexer.add("id", null, textSegment);

        List<Content> results = contentRetriever.retrieve(query);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).textSegment().metadata().getString("id")).isEqualTo("id");
        assertThat(results.get(0).textSegment().text()).isEqualTo(textSegment.text());
    }

    @BeforeEach
    void setUp() {
        directory = DirectoryFactory.tempDirectory();

        contentRetriever = LuceneContentRetriever.builder().directory(directory).build();
    }

    @AfterEach
    void tearDown() throws Exception {
        directory.close();
    }
}
