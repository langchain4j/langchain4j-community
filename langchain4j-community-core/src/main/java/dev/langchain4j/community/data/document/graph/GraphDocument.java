package dev.langchain4j.community.data.document.graph;

import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.Experimental;
import dev.langchain4j.data.document.Document;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a graph document consisting of nodes and relationships.
 *
 * @since 1.0.0-beta4
 */
@Experimental
public class GraphDocument {

    private final Set<GraphNode> nodes;
    private final Set<GraphEdge> relationships;
    private final Document source;

    public GraphDocument(Set<GraphNode> nodes, Set<GraphEdge> relationships, Document source) {
        this.nodes = copyIfNotNull(ensureNotNull(nodes, "nodes"));
        this.relationships = copyIfNotNull(ensureNotNull(relationships, "relationships"));
        this.source = ensureNotNull(source, "source");
    }

    @JsonProperty
    public Set<GraphNode> nodes() {
        return nodes;
    }

    @JsonProperty
    public Set<GraphEdge> relationships() {
        return relationships;
    }

    @JsonProperty
    public Document source() {
        return source;
    }

    public static GraphDocument from(Set<GraphNode> nodes, Set<GraphEdge> relationships, Document source) {
        return new GraphDocument(nodes, relationships, source);
    }

    public static GraphDocument from(Document source) {
        return new GraphDocument(Set.of(), Set.of(), source);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        GraphDocument that = (GraphDocument) object;
        return Objects.equals(nodes, that.nodes)
                && Objects.equals(relationships, that.relationships)
                && Objects.equals(source, that.source);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, relationships, source);
    }

    @Override
    public String toString() {
        return "GraphDocument{" + "nodes=" + nodes + ", relationships=" + relationships + ", source=" + source + '}';
    }
}
