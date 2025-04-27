package dev.langchain4j.community.data.document.graph;

import static dev.langchain4j.internal.Utils.copyIfNotNull;

import dev.langchain4j.Experimental;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a directed relationship between two GraphNodes in a graph.
 *
 * @since 1.0.0-beta4
 */
@Experimental
public class GraphEdge {

    private final GraphNode sourceNode;
    private final GraphNode targetNode;
    private final String type;
    private final Map<String, String> properties;

    public GraphEdge(GraphNode sourceNode, GraphNode targetNode, String type, Map<String, String> properties) {
        this.sourceNode = sourceNode;
        this.targetNode = targetNode;
        this.type = type;
        this.properties = copyIfNotNull(properties);
    }

    public GraphNode sourceNode() {
        return sourceNode;
    }

    public GraphNode targetNode() {
        return targetNode;
    }

    public String type() {
        return type;
    }

    public Map<String, String> properties() {
        return properties;
    }

    public static GraphEdge from(
            GraphNode sourceNode, GraphNode targetNode, String type, Map<String, String> properties) {
        return new GraphEdge(sourceNode, targetNode, type, properties);
    }

    public static GraphEdge from(GraphNode sourceNode, GraphNode targetNode, String type) {
        return new GraphEdge(sourceNode, targetNode, type, new HashMap<>());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        GraphEdge graphEdge = (GraphEdge) o;
        return Objects.equals(sourceNode, graphEdge.sourceNode)
                && Objects.equals(targetNode, graphEdge.targetNode)
                && Objects.equals(type, graphEdge.type)
                && Objects.equals(properties, graphEdge.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceNode, targetNode, type, properties);
    }

    @Override
    public String toString() {
        return "GraphEdge{" + "sourceNode="
                + sourceNode + ", targetNode="
                + targetNode + ", type='"
                + type + '\'' + ", properties="
                + properties + '}';
    }
}
