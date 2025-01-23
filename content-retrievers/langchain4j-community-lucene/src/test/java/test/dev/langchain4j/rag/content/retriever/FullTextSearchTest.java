package test.dev.langchain4j.rag.content.retriever;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.lucene.DirectoryFactory;
import dev.langchain4j.rag.content.retriever.lucene.LuceneContentRetriever;
import dev.langchain4j.rag.content.retriever.lucene.LuceneEmbeddingStore;
import dev.langchain4j.rag.query.Query;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FullTextSearchTest {

    private static final TextSegment[] hitTextSegments = {
        TextSegment.from("Lucene is a powerful search library.", metadataName("doc1")),
        TextSegment.from("Is Lucinity is not a library?", metadataName("doc2")),
        TextSegment.from("BM25Similarity is the default search term similarity algorithm.", metadataName("doc3")),
    };
    private static final TextSegment[] missTextSegments = {
        TextSegment.from("Aaaaah - some random text here.", metadataName("miss1"))
    };
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
    public void query1() {

        contentRetriever = LuceneContentRetriever.builder()
                .topNMatches(1)
                .directory(directory)
                .build();

        List<String> expectedTextSegments = new ArrayList<>();
        expectedTextSegments.add(hitTextSegments[2].text());

        List<Content> results = contentRetriever.retrieve(query);
        List<String> actualTextSegments =
                results.stream().map(content -> content.textSegment().text()).collect(Collectors.toList());
        Collections.sort(actualTextSegments);

        assertThat(results).hasSize(1);
        assertThat(actualTextSegments).isEqualTo(expectedTextSegments);
    }

    @Test
    public void queryAll() {

        contentRetriever = LuceneContentRetriever.builder()
                .matchUntilTopN()
                .directory(directory)
                .build();

        List<String> expectedTextSegments = new ArrayList<>();
        for (TextSegment textSegment : hitTextSegments) {
            expectedTextSegments.add(textSegment.text());
        }
        for (TextSegment textSegment : missTextSegments) {
            indexer.add(textSegment);
            expectedTextSegments.add(textSegment.text());
        }
        Collections.sort(expectedTextSegments);

        List<Content> results = contentRetriever.retrieve(query);
        List<String> actualTextSegments =
                results.stream().map(content -> content.textSegment().text()).collect(Collectors.toList());
        Collections.sort(actualTextSegments);

        assertThat(results).hasSize(hitTextSegments.length + missTextSegments.length);
        assertThat(actualTextSegments).isEqualTo(expectedTextSegments);
    }

    @Test
    public void queryContent() {

        contentRetriever = LuceneContentRetriever.builder().directory(directory).build();

        List<String> expectedTextSegments = new ArrayList<>();
        for (TextSegment textSegment : hitTextSegments) {
            expectedTextSegments.add(textSegment.text());
        }
        for (TextSegment textSegment : missTextSegments) {
            indexer.add(textSegment);
        }
        Collections.sort(expectedTextSegments);

        List<Content> results = contentRetriever.retrieve(query);
        List<String> actualTextSegments =
                results.stream().map(content -> content.textSegment().text()).collect(Collectors.toList());
        Collections.sort(actualTextSegments);

        assertThat(results).hasSize(hitTextSegments.length);
        assertThat(actualTextSegments).isEqualTo(expectedTextSegments);
    }

    @Test
    public void queryWithMaxTokens() {

        contentRetriever = LuceneContentRetriever.builder()
                .maxTokenCount(8)
                .directory(directory)
                .build();

        List<String> expectedTextSegments = new ArrayList<>();
        expectedTextSegments.add(hitTextSegments[0].text());

        List<Content> results = contentRetriever.retrieve(query);
        List<String> actualTextSegments =
                results.stream().map(content -> content.textSegment().text()).collect(Collectors.toList());
        Collections.sort(actualTextSegments);

        assertThat(results).hasSize(1);
        assertThat(actualTextSegments).isEqualTo(expectedTextSegments);
    }

    @Test
    public void retrieverWithBadTokenCountField() {

        contentRetriever = LuceneContentRetriever.builder()
                .maxTokenCount(8)
                .tokenCountFieldName("BAD_TOKEN_COUNT_FIELD_NAME")
                .directory(directory)
                .build();

        List<Content> results = contentRetriever.retrieve(query);

        // No limiting by token count, since wrong field is used
        assertThat(results).hasSize(hitTextSegments.length);
    }

    @Test
    public void retrieverWithWrongContentField() {

        contentRetriever = LuceneContentRetriever.builder()
                .contentFieldName("MY_CONTENT")
                .directory(directory)
                .build();

        List<Content> results = contentRetriever.retrieve(query);

        assertThat(results).isEmpty();
    }

    @BeforeEach
    public void setUp() {
        directory = DirectoryFactory.tempDirectory();
        indexer = LuceneEmbeddingStore.builder().directory(directory).build();
        for (TextSegment textSegment : hitTextSegments) {
            indexer.add(textSegment);
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        directory.close();
    }
}
