package dev.langchain4j.community.data.document.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphNodeTest {

    @Test
    void should_initialize_node_with_all_attributes_when_constructed() {
        // given
        String id = "node1";
        String type = "Person";
        Map<String, String> props = Map.of("name", "Alice");

        // when
        GraphNode node = new GraphNode(id, type, props);

        // then
        assertThat(node.id()).isEqualTo(id);
        assertThat(node.type()).isEqualTo(type);
        assertThat(node.properties()).containsEntry("name", "Alice");
    }

    @Test
    void should_use_default_values_when_type_and_properties_are_null() {
        // given
        String id = "node2";

        // when
        GraphNode node = new GraphNode(id, null, null);

        // then
        assertThat(node.id()).isEqualTo(id);
        assertThat(node.type()).isEqualTo("Node");
        assertThat(node.properties()).isEmpty();
    }

    @Test
    void should_create_node_with_all_fields_when_using_from_method() {
        // given
        String id = "node3";
        String type = "City";
        Map<String, String> props = Map.of("name", "Paris");

        // when
        GraphNode node = GraphNode.from(id, type, props);

        // then
        assertThat(node.id()).isEqualTo(id);
        assertThat(node.type()).isEqualTo("City");
        assertThat(node.properties()).containsEntry("name", "Paris");
    }

    @Test
    void should_create_node_with_default_type_and_empty_properties_when_using_from_method_with_only_id() {
        // given
        String id = "node4";

        // when
        GraphNode node = GraphNode.from(id);

        // then
        assertThat(node.id()).isEqualTo(id);
        assertThat(node.type()).isEqualTo("Node");
        assertThat(node.properties()).isEmpty();
    }

    @Test
    void should_return_true_when_nodes_are_equal_and_false_when_not_equal() {
        // given
        Map<String, String> props = Map.of("k", "v");

        GraphNode node1 = new GraphNode("id1", "TypeA", props);
        GraphNode node2 = new GraphNode("id1", "TypeA", props);
        GraphNode node3 = new GraphNode("id1", "TypeB", props);
        GraphNode node4 = new GraphNode("id2", "TypeA", props);

        // then
        assertThat(node1)
                .isEqualTo(node2)
                .hasSameHashCodeAs(node2)
                .isNotEqualTo(node3)
                .isNotEqualTo(node4)
                .isNotEqualTo(null)
                .isNotEqualTo("some string");
    }

    @Test
    void should_return_string_representation_containing_all_fields_when_toString_called() {
        // given
        GraphNode node = new GraphNode("id123", "Label", Map.of("x", "y"));

        // when
        String str = node.toString();

        // then
        assertThat(str).contains("id='id123'").contains("type='Label'").contains("properties={x=y}");
    }
}
