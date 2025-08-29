package dev.langchain4j.community.data.document.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphEdgeTest {

    @Test
    void should_initialize_edge_with_all_attributes_when_constructed() {
        // given
        GraphNode source = mock(GraphNode.class);
        GraphNode target = mock(GraphNode.class);
        String type = "RELATED_TO";
        Map<String, String> properties = Map.of("key", "value");

        // when
        GraphEdge edge = new GraphEdge(source, target, type, properties);

        // then
        assertThat(edge.sourceNode()).isEqualTo(source);
        assertThat(edge.targetNode()).isEqualTo(target);
        assertThat(edge.type()).isEqualTo(type);
        assertThat(edge.properties()).containsEntry("key", "value");
    }

    @Test
    void should_create_empty_properties_map_when_properties_are_null() {
        // given
        GraphNode source = mock(GraphNode.class);
        GraphNode target = mock(GraphNode.class);
        String type = "RELATES_TO";

        // when
        GraphEdge edge = new GraphEdge(source, target, type, null);

        // then
        assertThat(edge.properties()).isEmpty();
    }

    @Test
    void should_create_edge_with_properties_when_using_from_method() {
        // given
        GraphNode source = mock(GraphNode.class);
        GraphNode target = mock(GraphNode.class);
        String type = "CONNECTED_TO";
        Map<String, String> props = Map.of("foo", "bar");

        // when
        GraphEdge edge = GraphEdge.from(source, target, type, props);

        // then
        assertThat(edge.sourceNode()).isEqualTo(source);
        assertThat(edge.targetNode()).isEqualTo(target);
        assertThat(edge.type()).isEqualTo(type);
        assertThat(edge.properties()).containsEntry("foo", "bar");
    }

    @Test
    void should_create_edge_with_empty_properties_when_using_from_method_without_properties() {
        // given
        GraphNode source = mock(GraphNode.class);
        GraphNode target = mock(GraphNode.class);
        String type = "LINKED_TO";

        // when
        GraphEdge edge = GraphEdge.from(source, target, type);

        // then
        assertThat(edge.sourceNode()).isEqualTo(source);
        assertThat(edge.targetNode()).isEqualTo(target);
        assertThat(edge.type()).isEqualTo(type);
        assertThat(edge.properties()).isEmpty();
    }

    @Test
    void should_return_true_when_edges_are_equal_and_false_when_not_equal() {
        // given
        GraphNode source = mock(GraphNode.class);
        GraphNode target = mock(GraphNode.class);
        String type = "RELATES_TO";
        Map<String, String> props = Map.of("k", "v");

        GraphEdge edge1 = new GraphEdge(source, target, type, props);
        GraphEdge edge2 = new GraphEdge(source, target, type, props);
        GraphEdge edge3 = new GraphEdge(source, target, "DIFFERENT_TYPE", props);

        // then
        assertThat(edge1).isEqualTo(edge2).hasSameHashCodeAs(edge2);
        assertThat(edge1).isNotEqualTo(edge3);
        assertThat(edge1).isNotEqualTo(null).isNotEqualTo("some string");
    }

    @Test
    void should_return_string_representation_containing_all_fields_when_toString_called() {
        // given
        GraphNode source = mock(GraphNode.class);
        GraphNode target = mock(GraphNode.class);
        String type = "RELATES_TO";
        Map<String, String> props = Map.of("foo", "bar");

        GraphEdge edge = new GraphEdge(source, target, type, props);

        // when
        String str = edge.toString();

        // then
        assertThat(str)
                .contains("sourceNode=")
                .contains("targetNode=")
                .contains("type='RELATES_TO'")
                .contains("properties={foo=bar}");
    }
}
