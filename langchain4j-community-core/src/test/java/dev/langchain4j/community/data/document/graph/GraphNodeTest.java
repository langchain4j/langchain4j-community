package dev.langchain4j.community.data.document.graph;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphNodeTest {

    @Test
    void test_constructor_and_getters() {
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
    void test_constructor_with_null_type_and_properties() {
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
    void test_static_factory_method_with_all_fields() {
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
    void test_static_factory_method_with_only_id() {
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
    void test_equals_and_hashcode() {
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
    void test_to_string_contains_all_fields() {
        // given
        GraphNode node = new GraphNode("id123", "Label", Map.of("x", "y"));

        // when
        String str = node.toString();

        // then
        assertThat(str).contains("id='id123'").contains("type='Label'").contains("properties={x=y}");
    }
}
