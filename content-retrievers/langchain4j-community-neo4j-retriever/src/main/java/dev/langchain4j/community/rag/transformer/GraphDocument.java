package dev.langchain4j.community.rag.transformer;

import dev.langchain4j.data.document.Document;
import java.util.Objects;
import java.util.Set;

public class GraphDocument {
    private Set<Node> nodes;
    private Set<Edge> relationships;
    private Document source;

    public GraphDocument(Set<GraphDocument.Node> nodes, Set<GraphDocument.Edge> relationships, Document source) {
        this.nodes = nodes;
        this.relationships = relationships;
        this.source = source;
    }

    public Set<Node> getNodes() {
        return nodes;
    }

    public Set<Edge> getRelationships() {
        return relationships;
    }

    public Document getSource() {
        return source;
    }

    @Override
    public boolean equals(final Object object) {
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

    /*
    Node entity
    */
    public static class Node {
        private String id;
        private String type;

        public Node(String id, String type) {
            this.id = id;
            this.type = type;
        }

        public String getId() {
            return id;
        }

        public String getType() {
            return type;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) return true;
            if (object == null || getClass() != object.getClass()) return false;
            Node node = (Node) object;
            return Objects.equals(id, node.id) && Objects.equals(type, node.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, type);
        }
    }

    /*
    Edge entity
    */
    public static class Edge {
        private Node sourceNode;
        private Node targetNode;
        private String type;

        public Edge(final Node sourceNode, final Node targetNode, final String type) {
            this.sourceNode = sourceNode;
            this.targetNode = targetNode;
            this.type = type;
        }

        public Node getSourceNode() {
            return sourceNode;
        }

        public Node getTargetNode() {
            return targetNode;
        }

        public String getType() {
            return type;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) return true;
            if (object == null || getClass() != object.getClass()) return false;
            Edge edge = (Edge) object;
            return Objects.equals(sourceNode, edge.sourceNode)
                    && Objects.equals(targetNode, edge.targetNode)
                    && Objects.equals(type, edge.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sourceNode, targetNode, type);
        }
    }
}
