package dev.langchain4j.community.data.document.transformer.graph;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.data.document.Document;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphTransformerTest {

    /**
     * A mock {@link GraphTransformer} which extract nodes from YAML document
     */
    static class MockGraphTransformer implements GraphTransformer {

        @Override
        public GraphDocument transform(Document document) {
            if (document.text().contains("valid")) {
                return GraphDocument.from(new HashSet<>(), new HashSet<>(), document);
            }
            return null;
        }
    }

    @Test
    void should_return_empty_when_null() {
        GraphTransformer graphTransformer = new MockGraphTransformer();

        // when
        List<GraphDocument> graphDocuments = graphTransformer.transformAll(null);

        // then
        assertThat(graphDocuments).isEmpty();
    }

    @Test
    void should_return_empty_when_empty() {
        GraphTransformer graphTransformer = new MockGraphTransformer();

        // when
        List<GraphDocument> graphDocuments = graphTransformer.transformAll(List.of());

        // then
        assertThat(graphDocuments).isEmpty();
    }

    @Test
    void should_return_transformed_documents() {
        GraphTransformer graphTransformer = new MockGraphTransformer();

        // given
        Document doc1 = Document.from("This is a valid document.");
        Document doc2 = Document.from("Another valid one.");
        Document doc3 = Document.from("This should be filtered out.");
        List<Document> input = List.of(doc1, doc2, doc3);

        // when
        List<GraphDocument> graphDocuments = graphTransformer.transformAll(input);

        // then
        assertThat(graphDocuments).hasSize(2).allMatch(g -> g.source().text().contains("valid"));
    }
}
