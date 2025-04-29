package dev.langchain4j.community.data.document.graph;

import static dev.langchain4j.internal.Utils.copy;
import static dev.langchain4j.internal.Utils.getOrDefault;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Experimental;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a node in a graph with associated properties.
 *
 * @since 1.0.0-beta4
 */
@Experimental
public class GraphNode {

    private final String id;
    private final String type;
    private final Map<String, String> properties;

    public GraphNode(String id, String type, Map<String, String> properties) {
        this.id = id;
        this.type = getOrDefault(type, "Node");
        this.properties = copy(properties);
    }

    @JsonProperty
    public String id() {
        return id;
    }

    @JsonProperty
    public String type() {
        return type;
    }

    @JsonProperty
    public Map<String, String> properties() {
        return properties;
    }

    public static GraphNode from(String id, String type, Map<String, String> properties) {
        return new GraphNode(id, type, properties);
    }

    public static GraphNode from(String id, String type) {
        return new GraphNode(id, type, Map.of());
    }

    public static GraphNode from(String id) {
        return new GraphNode(id, null, Map.of());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        GraphNode graphNode = (GraphNode) o;
        return Objects.equals(id, graphNode.id)
                && Objects.equals(type, graphNode.type)
                && Objects.equals(properties, graphNode.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, properties);
    }

    @Override
    public String toString() {
        return "GraphNode{" + "id='" + id + '\'' + ", type='" + type + '\'' + ", properties=" + properties + '}';
    }
}
