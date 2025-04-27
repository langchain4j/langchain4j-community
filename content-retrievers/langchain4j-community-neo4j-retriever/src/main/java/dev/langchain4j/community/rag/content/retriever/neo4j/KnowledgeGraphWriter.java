package dev.langchain4j.community.rag.content.retriever.neo4j;

import static dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jUtils.generateMD5;
import static dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jUtils.sanitizeOrThrows;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.community.data.document.graph.GraphDocument;
import dev.langchain4j.data.document.Document;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Experimental
public class KnowledgeGraphWriter {

    /* default configs */
    public static final String DEFAULT_ID_PROP = "id";
    public static final String DEFAULT_TEXT_PROP = "text";
    public static final String DEFAULT_LABEL = "__Entity__";
    public static final String DEFAULT_CONS_NAME = "knowledge_cons";
    public static final String DEFAULT_REL_TYPE = "HAS_ENTITY";

    final String label;
    final String relType;
    final String sanitizedLabel;
    final String sanitizedRelType;
    final String constraintName;

    final String idProperty;
    final String sanitizedIdProperty;
    final String textProperty;
    final String sanitizedTextProperty;

    private final Neo4jGraph graph;

    public KnowledgeGraphWriter(
            Neo4jGraph graph,
            String idProperty,
            String label,
            String textProperty,
            String relType,
            String constraintName) {
        this.graph = ensureNotNull(graph, "graph");
        this.label = getOrDefault(label, DEFAULT_LABEL);
        this.relType = getOrDefault(relType, DEFAULT_REL_TYPE);
        this.idProperty = getOrDefault(idProperty, DEFAULT_ID_PROP);
        this.textProperty = getOrDefault(textProperty, DEFAULT_TEXT_PROP);
        this.constraintName = getOrDefault(constraintName, DEFAULT_CONS_NAME);

        /* sanitize labels and property names, to prevent from Cypher Injections */
        this.sanitizedLabel = sanitizeOrThrows(this.label, "label");
        this.sanitizedRelType = sanitizeOrThrows(this.relType, "relType");
        this.sanitizedIdProperty = sanitizeOrThrows(this.idProperty, "idProperty");
        this.sanitizedTextProperty = sanitizeOrThrows(this.textProperty, "textProperty");

        createConstraint();
    }

    public static Builder builder() {
        return new Builder();
    }

    private void createConstraint() {
        // Check and create constraint if not exists
        final String constraintQuery = String.format(
                "CREATE CONSTRAINT %s IF NOT EXISTS FOR (b:%s) REQUIRE b.%s IS UNIQUE;",
                constraintName, sanitizedLabel, sanitizedIdProperty);
        graph.executeWrite(constraintQuery);
        graph.refreshSchema();
    }

    public void addGraphDocuments(List<GraphDocument> graphDocuments, boolean includeSource) {

        for (GraphDocument graphDoc : graphDocuments) {
            Document source = graphDoc.source();

            // Import nodes
            Map<String, Object> nodeParams = new HashMap<>();
            nodeParams.put(
                    "data", graphDoc.nodes().stream().map(Neo4jUtils::toMap).toList());

            if (includeSource) {
                // create a copyOf metadata, not to update existing graphDoc,
                // subsequent tests could potentially fail
                final Map<String, Object> metadata =
                        new HashMap<>(Map.copyOf(source.metadata().toMap()));
                if (!metadata.containsKey(idProperty)) {
                    metadata.put(idProperty, generateMD5(source.text()));
                }
                final Map<String, Object> document = Map.of("metadata", metadata, "text", source.text());
                nodeParams.put("document", document);
            }

            String nodeImportQuery = getNodeImportQuery(includeSource);
            graph.executeWrite(nodeImportQuery, nodeParams);

            // Import relationships
            List<Map<String, String>> relData = graphDoc.relationships().stream()
                    .map(rel -> Map.of(
                            "source", rel.sourceNode().id(),
                            "source_label", rel.sourceNode().type(),
                            "target", rel.targetNode().id(),
                            "target_label", rel.targetNode().type(),
                            "type", rel.type().replace(" ", "_").toUpperCase()))
                    .toList();

            String relImportQuery = getRelImportQuery();
            graph.executeWrite(relImportQuery, Map.of("data", relData));
        }
    }

    private String getNodeImportQuery(boolean includeSource) {

        String includeDocsQuery = getIncludeDocsQuery(includeSource);
        final String withDocsRel = includeSource ? String.format("MERGE (d)-[:%s]->(source) ", relType) : "";

        return includeDocsQuery + "UNWIND $data AS row "
                + String.format("MERGE (source:%1$s {%2$s: row.id}) ", sanitizedLabel, sanitizedIdProperty)
                + withDocsRel
                + "WITH source, row "
                + "SET source:$(row.type) "
                + "RETURN count(*) as total";
    }

    private String getIncludeDocsQuery(boolean includeSource) {
        if (!includeSource) {
            return "";
        }
        return String.format(
                """
                        MERGE (d:Document {%1$s: $document.metadata.%1$s})
                        SET d.%2$s = $document.text
                        SET d += $document.metadata
                        WITH d
                        """,
                sanitizedIdProperty, sanitizedTextProperty);
    }

    private String getRelImportQuery() {

        return String.format(
                """
                        UNWIND $data AS row
                        MERGE (source:%1$s {%2$s: row.source})
                        MERGE (target:%1$s {%2$s: row.target})
                        WITH source, target, row
                        MERGE (source)-[rel:$(toString(row.type) + '')]->(target)
                        RETURN distinct 'done'
                        """,
                sanitizedLabel, sanitizedIdProperty);
    }

    public static class Builder {

        private String label;
        private String idProperty;
        private String textProperty;
        private String relType;
        private String constraintName;
        private Neo4jGraph graph;

        /**
         * @param graph the {@link Neo4jGraph} (required)
         */
        public Builder graph(Neo4jGraph graph) {
            this.graph = graph;
            return this;
        }

        /**
         * @param idProperty the entity id, to be used with {@link #addGraphDocuments(List, boolean)}
         */
        public Builder idProperty(String idProperty) {
            this.idProperty = idProperty;
            return this;
        }

        /**
         * @param textProperty the document text, to be used with {@link #addGraphDocuments(List, boolean)}
         *                     if the second parameter is true
         */
        public Builder textProperty(String textProperty) {
            this.textProperty = textProperty;
            return this;
        }

        /**
         * @param label the entity label, to be used with {@link #addGraphDocuments(List, boolean)}
         *              (default: "__Entity__")
         */
        public Builder label(String label) {
            this.label = label;
            return this;
        }

        /**
         * @param relType the entity relationship types, to be used with {@link #addGraphDocuments(List, boolean)}
         *                (default "HAS_ENTITY")
         */
        public Builder relType(String relType) {
            this.relType = relType;
            return this;
        }

        /**
         * @param constraintName the constraint name (default: "knowledge_cons")
         */
        public Builder constraintName(String constraintName) {
            this.constraintName = constraintName;
            return this;
        }

        public KnowledgeGraphWriter build() {
            return new KnowledgeGraphWriter(graph, idProperty, label, textProperty, relType, constraintName);
        }
    }
}
