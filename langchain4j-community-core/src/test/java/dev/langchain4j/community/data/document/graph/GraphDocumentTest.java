package dev.langchain4j.community.data.document.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import dev.langchain4j.data.document.Document;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GraphDocumentTest {

    @Test
    void should_return_true_when_documents_are_equal_and_false_when_not_equal() {

        // given
        Document source = mock(Document.class);
        GraphNode node = mock(GraphNode.class);
        Set<GraphNode> nodes = Set.of(node);
        GraphEdge edge = mock(GraphEdge.class);
        Set<GraphEdge> relationships = Set.of(edge);

        // when
        GraphDocument doc1 = new GraphDocument(nodes, relationships, source);
        GraphDocument doc2 = new GraphDocument(nodes, relationships, source);
        GraphDocument doc3 = new GraphDocument(new HashSet<>(), new HashSet<>(), source);

        // then
        assertThat(doc1)
                .isEqualTo(doc1)
                .hasSameHashCodeAs(doc1)
                .isEqualTo(doc2)
                .hasSameHashCodeAs(doc2)
                .isNotEqualTo(doc3)
                .doesNotHaveSameHashCodeAs(doc3)
                .isNotEqualTo(null);
    }

    @Test
    void should_create_graph_document_with_all_attributes_when_provided() {

        // given
        GraphNode node = mock(GraphNode.class);
        GraphEdge edge = mock(GraphEdge.class);
        Document source = mock(Document.class);
        Set<GraphNode> nodes = Set.of(node);
        Set<GraphEdge> relationships = Set.of(edge);

        // when
        GraphDocument doc = GraphDocument.from(nodes, relationships, source);

        // then
        assertThat(nodes).isEqualTo(doc.nodes());
        assertThat(relationships).isEqualTo(doc.relationships());
        assertThat(source).isEqualTo(doc.source());
    }

    @Test
    void should_create_graph_document_with_empty_collections_when_only_source_provided() {

        // given
        Document source = mock(Document.class);

        // when
        GraphDocument doc = GraphDocument.from(source);

        // then
        assertThat(doc.nodes()).isEmpty();
        assertThat(doc.relationships()).isEmpty();
        assertThat(source).isEqualTo(doc.source());
    }

    @Test
    void should_throw_exception_when_source_is_null() {

        assertThatThrownBy(() -> new GraphDocument(Set.of(), Set.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("source cannot be null");
    }

    @Test
    void should_return_string_representation_when_toString_called() {

        // given
        Document source = mock(Document.class);

        // when
        GraphDocument doc = new GraphDocument(new HashSet<>(), new HashSet<>(), source);
        String toString = doc.toString();

        // then
        assertThat(toString).contains("nodes=").contains("relationships=").contains("source=");
    }
}
