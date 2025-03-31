package dev.langchain4j.community.rag.content.retriever.lucene;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.rag.content.retriever.lucene.utility.TextEmbedding;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.query.Query;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FullTextSearchIT {

    private static final TextEmbedding[] hits = {
        TextEmbedding.fromResource("hitDoc1.txt"),
        TextEmbedding.fromResource("hitDoc2.txt"),
        TextEmbedding.fromResource("hitDoc3.txt"),
    };
    private static final TextEmbedding[] misses = {
        TextEmbedding.fromResource("missDoc1.txt"),
    };
    private static final Query query =
            Query.from(TextEmbedding.fromResource("query1.txt").text().text());

    private static Metadata metadataName(String name) {
        Metadata metadata = new Metadata();
        metadata.put("name", name);
        return metadata;
    }

    private Directory directory;
    private LuceneEmbeddingStore indexer;
    private LuceneContentRetriever contentRetriever;

    @Test
    @DisplayName("Test retriever returning just 1 max result")
    void query1() {

        contentRetriever = LuceneContentRetriever.builder()
                .maxResults(1)
                .directory(directory)
                .build();

        List<String> expectedTextSegments = new ArrayList<>();
        expectedTextSegments.add(hits[1].text().text());

        List<Content> results = contentRetriever.retrieve(query);
        List<String> actualTextSegments =
                results.stream().map(content -> content.textSegment().text()).collect(Collectors.toList());
        Collections.sort(actualTextSegments);

        assertThat(results).hasSize(1);
        assertThat(actualTextSegments).isEqualTo(expectedTextSegments);
    }

    @Test
    @DisplayName("Test retriever returning all resuts, not just matchine ones")
    void queryAll() {

        contentRetriever = LuceneContentRetriever.builder()
                .matchUntilMaxResults()
                .directory(directory)
                .build();

        List<String> expectedTextSegments = new ArrayList<>();
        for (TextEmbedding textEmbedding : hits) {
            expectedTextSegments.add(textEmbedding.text().text());
        }
        for (TextEmbedding textEmbedding : misses) {
            indexer.add(textEmbedding.text());
            expectedTextSegments.add(textEmbedding.text().text());
        }
        Collections.sort(expectedTextSegments);

        List<Content> results = contentRetriever.retrieve(query);
        List<String> actualTextSegments =
                results.stream().map(content -> content.textSegment().text()).collect(Collectors.toList());
        Collections.sort(actualTextSegments);

        assertThat(results).hasSize(hits.length + misses.length);
        assertThat(actualTextSegments).isEqualTo(expectedTextSegments);
    }

    @Test
    @DisplayName("Test retriever using default settings (except directory)")
    void queryContent() {

        contentRetriever = LuceneContentRetriever.builder().directory(directory).build();

        List<String> expectedTextSegments = new ArrayList<>();
        for (TextEmbedding textEmbedding : hits) {
            expectedTextSegments.add(textEmbedding.text().text());
        }
        for (TextEmbedding textEmbedding : misses) {
            indexer.add(textEmbedding.text());
        }
        Collections.sort(expectedTextSegments);

        List<Content> results = contentRetriever.retrieve(query);
        List<String> actualTextSegments =
                results.stream().map(content -> content.textSegment().text()).collect(Collectors.toList());
        Collections.sort(actualTextSegments);

        assertThat(results).hasSize(hits.length);
        assertThat(actualTextSegments).isEqualTo(expectedTextSegments);
    }

    @Test
    @DisplayName("Test retriever returning up to max tokens")
    void queryWithMaxTokens() {

        contentRetriever = LuceneContentRetriever.builder()
                .maxTokens(8)
                .directory(directory)
                .build();

        List<String> expectedTextSegments = new ArrayList<>();
        expectedTextSegments.add(hits[0].text().text());

        List<Content> results = contentRetriever.retrieve(query);
        List<String> actualTextSegments =
                results.stream().map(content -> content.textSegment().text()).collect(Collectors.toList());
        Collections.sort(actualTextSegments);

        assertThat(results).hasSize(1);
        assertThat(actualTextSegments).isEqualTo(expectedTextSegments);
    }

    @Test
    @DisplayName("Test retriever return only matching documents")
    void queryWithMetadataFields() {

        Metadata metadata = metadataName("doc1");
        metadata.put("float", -1F);
        metadata.put("double", -1D);
        metadata.put("int", -1);
        metadata.put("long", -1L);
        TextSegment textSegment = TextSegment.from("Eye of the tiger", metadata);

        indexer.add(textSegment);

        contentRetriever = LuceneContentRetriever.builder()
                .directory(directory)
                .onlyMatches()
                .build();

        List<Content> results = contentRetriever.retrieve(Query.from("Tiger"));

        assertThat(results).hasSize(1);
        TextSegment actualTextSegment = results.get(0).textSegment();
        assertThat(actualTextSegment.text()).isEqualTo(textSegment.text());

        Metadata actualMetadata = actualTextSegment.metadata();
        assertThat(actualMetadata.getString("name")).isEqualTo(metadata.getString("name"));
        assertThat(actualMetadata.getFloat("float")).isEqualTo(-1);
        assertThat(actualMetadata.getDouble("double")).isEqualTo(-1);
        assertThat(actualMetadata.getInteger("int")).isEqualTo(-1);
        assertThat(actualMetadata.getLong("long")).isEqualTo(-1);
    }

    @Test
    @DisplayName("Test retriever returning documents greater than a minimum score")
    void queryWithMinScore() {

        contentRetriever = LuceneContentRetriever.builder()
                .minScore(0.3)
                .directory(directory)
                .build();

        List<String> expectedTextSegments = new ArrayList<>();
        expectedTextSegments.add(hits[1].text().text());
        expectedTextSegments.add(hits[0].text().text());

        List<Content> results = contentRetriever.retrieve(query);
        List<String> actualTextSegments =
                results.stream().map(content -> content.textSegment().text()).collect(Collectors.toList());
        Collections.sort(actualTextSegments);

        assertThat(results).hasSize(2);
        assertThat(actualTextSegments).isEqualTo(expectedTextSegments);
    }

    @Test
    @DisplayName("Test retriever where a token counts are not found due to an incorrect field name")
    void retrieverWithBadTokenCountField() {

        contentRetriever = LuceneContentRetriever.builder()
                .tokenCountFieldName("BAD_TOKEN_COUNT_FIELD_NAME")
                .directory(directory)
                .build();

        List<Content> results = contentRetriever.retrieve(query);

        // No limiting by token count, since wrong field is used
        assertThat(results).hasSize(hits.length);
    }

    @Test
    @DisplayName("Test retriever using a null query")
    void retrieverWithNullQuery() {

        contentRetriever = LuceneContentRetriever.builder().directory(directory).build();

        List<Content> results = contentRetriever.retrieve(null);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Test retriever where documents are not found due to an incorrect field name")
    void retrieverWithWrongContentField() {

        contentRetriever = LuceneContentRetriever.builder()
                .contentFieldName("MY_CONTENT")
                .directory(directory)
                .build();

        List<Content> results = contentRetriever.retrieve(query);

        assertThat(results).isEmpty();
    }

    @BeforeEach
    void setUp() {
        directory = DirectoryFactory.tempDirectory();
        indexer = LuceneEmbeddingStore.builder().directory(directory).build();
        for (TextEmbedding textEmbedding : hits) {
            indexer.add(textEmbedding.text());
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        directory.close();
    }
}
