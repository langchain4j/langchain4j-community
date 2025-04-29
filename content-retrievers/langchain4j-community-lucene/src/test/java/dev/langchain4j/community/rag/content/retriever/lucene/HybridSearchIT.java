package dev.langchain4j.community.rag.content.retriever.lucene;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.rag.content.retriever.lucene.utility.TextEmbedding;
import dev.langchain4j.community.rag.content.retriever.lucene.utility.TextEmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.rag.query.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HybridSearchIT {

    private static final TextEmbedding[] hits = {
        TextEmbedding.fromResource("hitDoc1.txt"),
        TextEmbedding.fromResource("hitDoc2.txt"),
        TextEmbedding.fromResource("hitDoc3.txt"),
    };
    private static final TextEmbedding[] misses = {
        TextEmbedding.fromResource("missDoc1.txt"),
    };

    private Directory directory;
    private LuceneEmbeddingStore indexer;
    private LuceneContentRetriever contentRetriever;

    @Test
    @DisplayName("Test retriever using hybrid search with logical query")
    void hybridQuery1() {

        TextEmbedding query = TextEmbedding.fromResource("query1.txt");
        String queryText = query.text().text();

        List<String> expectedTextSegments = new ArrayList<>();
        expectedTextSegments.add(hits[1].text().text());
        expectedTextSegments.add(hits[0].text().text());
        expectedTextSegments.add(hits[2].text().text());

        contentRetriever = LuceneContentRetriever.builder()
                .directory(directory)
                .embeddingModel(new TextEmbeddingModel(query))
                .minScore(0.4f)
                .build();

        List<Content> results = contentRetriever.retrieve(Query.from(queryText));
        debugQuery(query, results);
        List<String> actualTextSegments =
                results.stream().map(content -> content.textSegment().text()).collect(Collectors.toList());

        assertThat(results).hasSize(3);
        assertThat(actualTextSegments).isEqualTo(expectedTextSegments);
    }

    @Test
    @DisplayName("Test retriever using hybrid search with query that needs an embedding model")
    void hybridQuery2() {

        TextEmbedding query = TextEmbedding.fromResource("query2.txt");
        String queryText = query.text().text();

        List<String> expectedTextSegments = new ArrayList<>();
        expectedTextSegments.add(hits[0].text().text());

        contentRetriever = LuceneContentRetriever.builder()
                .directory(directory)
                .embeddingModel(new TextEmbeddingModel(query))
                .minScore(0.4f)
                .build();

        List<Content> results = contentRetriever.retrieve(Query.from(queryText));
        debugQuery(query, results);
        List<String> actualTextSegments =
                results.stream().map(content -> content.textSegment().text()).collect(Collectors.toList());

        assertThat(results).hasSize(1);
        assertThat(actualTextSegments).isEqualTo(expectedTextSegments);
    }

    @Test
    @DisplayName("Test retriever using hybrid search with unrelated query")
    void hybridQuery3() {

        TextEmbedding query = TextEmbedding.fromResource("query3.txt");
        String queryText = query.text().text();

        contentRetriever = LuceneContentRetriever.builder()
                .directory(directory)
                .embeddingModel(new TextEmbeddingModel(query))
                .minScore(0.4f)
                .build();

        List<Content> results = contentRetriever.retrieve(Query.from(queryText));
        debugQuery(query, results);

        assertThat(results).hasSize(0);
    }

    @BeforeEach
    void setUp() {
        directory = DirectoryFactory.tempDirectory();
        indexer = LuceneEmbeddingStore.builder().directory(directory).build();
        for (TextEmbedding textEmbedding : hits) {
            indexer.add(textEmbedding.embedding(), textEmbedding.text());
        }
        for (TextEmbedding textEmbedding : misses) {
            indexer.add(textEmbedding.embedding(), textEmbedding.text());
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        directory.close();
    }

    private void debugQuery(TextEmbedding query, List<Content> results) {
        System.out.printf("%n>> %s%n", query.text().text());
        for (Content content : results) {
            System.out.printf(
                    "%f %s%n",
                    content.metadata().get(ContentMetadata.SCORE),
                    content.textSegment().text());
        }
    }
}
