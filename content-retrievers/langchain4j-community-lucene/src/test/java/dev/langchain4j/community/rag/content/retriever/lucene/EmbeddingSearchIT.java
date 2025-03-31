package dev.langchain4j.community.rag.content.retriever.lucene;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.rag.content.retriever.lucene.utility.TextEmbedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmbeddingSearchIT {

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

    @Test
    @DisplayName("Test retriever using hybrid search with logical query")
    void hybridQuery1() {

        TextEmbedding query = TextEmbedding.fromResource("query1.txt");

        List<String> expectedTextSegments = new ArrayList<>();
        expectedTextSegments.add(hits[2].text().text());
        expectedTextSegments.add(hits[0].text().text());
        expectedTextSegments.add(hits[1].text().text());

        EmbeddingSearchRequest embeddingSearchRequest = new EmbeddingSearchRequest(query.embedding(), 10, 0.4, null);
        List<EmbeddingMatch<TextSegment>> results =
                indexer.search(embeddingSearchRequest).matches();

        debugQuery(query, results);
        List<String> actualTextSegments =
                results.stream().map(match -> match.embedded().text()).collect(Collectors.toList());

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

        EmbeddingSearchRequest embeddingSearchRequest = new EmbeddingSearchRequest(query.embedding(), 10, 0.4, null);
        List<EmbeddingMatch<TextSegment>> results =
                indexer.search(embeddingSearchRequest).matches();

        debugQuery(query, results);
        List<String> actualTextSegments =
                results.stream().map(match -> match.embedded().text()).collect(Collectors.toList());

        assertThat(results).hasSize(1);
        assertThat(actualTextSegments).isEqualTo(expectedTextSegments);
    }

    @Test
    @DisplayName("Test retriever using hybrid search with unrelated query")
    void hybridQuery3() {

        TextEmbedding query = TextEmbedding.fromResource("query3.txt");
        String queryText = query.text().text();

        EmbeddingSearchRequest embeddingSearchRequest = new EmbeddingSearchRequest(query.embedding(), 10, 0.4, null);
        List<EmbeddingMatch<TextSegment>> results =
                indexer.search(embeddingSearchRequest).matches();

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

    private void debugQuery(TextEmbedding query, List<EmbeddingMatch<TextSegment>> results) {
        System.out.printf("%n>> %s%n", query.text().text());
        for (EmbeddingMatch<TextSegment> match : results) {
            System.out.printf("%f %s%n", match.score(), match.embedded().text());
        }
    }
}
