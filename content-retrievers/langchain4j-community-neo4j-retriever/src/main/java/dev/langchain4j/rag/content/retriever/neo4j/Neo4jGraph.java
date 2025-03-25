package dev.langchain4j.rag.content.retriever.neo4j;

import dev.langchain4j.internal.ValidationUtils;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.summary.ResultSummary;

public class Neo4jGraph implements AutoCloseable {

    public static class Builder {
        private Driver driver;

        /**
         * @param driver the {@link Driver} (required)
         */
        Builder driver(Driver driver) {
            this.driver = driver;
            return this;
        }

        Neo4jGraph build() {
            return new Neo4jGraph(driver);
        }
    }

    private static final String NODE_PROPERTIES_QUERY =
            """
                    CALL apoc.meta.data()
                    YIELD label, other, elementType, type, property
                    WHERE NOT type = "RELATIONSHIP" AND elementType = "node"
                    WITH label AS nodeLabels, collect({property:property, type:type}) AS properties
                    RETURN {labels: nodeLabels, properties: properties} AS output
                    """;

    private static final String REL_PROPERTIES_QUERY =
            """
                    CALL apoc.meta.data()
                    YIELD label, other, elementType, type, property
                    WHERE NOT type = "RELATIONSHIP" AND elementType = "relationship"
                    WITH label AS nodeLabels, collect({property:property, type:type}) AS properties
                    RETURN {type: nodeLabels, properties: properties} AS output
                    """;

    private static final String RELATIONSHIPS_QUERY =
            """
                    CALL apoc.meta.data()
                    YIELD label, other, elementType, type, property
                    WHERE type = "RELATIONSHIP" AND elementType = "node"
                    UNWIND other AS other_node
                    RETURN {start: label, type: property, end: toString(other_node)} AS output
                    """;

    private final Driver driver;

    private String schema;

    public Neo4jGraph(final Driver driver) {

        this.driver = ValidationUtils.ensureNotNull(driver, "driver");
        this.driver.verifyConnectivity();
        try {
            refreshSchema();
        } catch (ClientException e) {
            if ("Neo.ClientError.Procedure.ProcedureNotFound".equals(e.code())) {
                throw new Neo4jException("Please ensure the APOC plugin is installed in Neo4j", e);
            }
            throw e;
        }
    }

    public String getSchema() {
        return schema;
    }

    static Builder builder() {
        return new Builder();
    }

    public ResultSummary executeWrite(String queryString) {

        try (Session session = this.driver.session()) {
            return session.executeWrite(tx -> tx.run(queryString).consume());
        } catch (ClientException e) {
            throw new Neo4jException("Error executing query: " + queryString, e);
        }
    }

    public List<Record> executeRead(String queryString) {

        return this.driver.executableQuery(queryString).execute().records();
    }

    public void refreshSchema() {

        List<String> nodeProperties = formatNodeProperties(executeRead(NODE_PROPERTIES_QUERY));
        List<String> relationshipProperties = formatRelationshipProperties(executeRead(REL_PROPERTIES_QUERY));
        List<String> relationships = formatRelationships(executeRead(RELATIONSHIPS_QUERY));

        this.schema = "Node properties are the following:\n" + String.join("\n", nodeProperties)
                + "\n\n" + "Relationship properties are the following:\n"
                + String.join("\n", relationshipProperties)
                + "\n\n" + "The relationships are the following:\n"
                + String.join("\n", relationships);
    }

    private List<String> formatNodeProperties(List<Record> records) {

        return records.stream()
                .map(this::getOutput)
                .map(r -> String.format(
                        "%s %s",
                        r.asMap().get("labels"), formatMap(r.get("properties").asList(Value::asMap))))
                .toList();
    }

    private List<String> formatRelationshipProperties(List<Record> records) {

        return records.stream()
                .map(this::getOutput)
                .map(r -> String.format(
                        "%s %s", r.get("type"), formatMap(r.get("properties").asList(Value::asMap))))
                .toList();
    }

    private List<String> formatRelationships(List<Record> records) {

        return records.stream()
                .map(r -> getOutput(r).asMap())
                .map(r -> String.format("(:%s)-[:%s]->(:%s)", r.get("start"), r.get("type"), r.get("end")))
                .toList();
    }

    private Value getOutput(Record record) {

        return record.get("output");
    }

    private String formatMap(List<Map<String, Object>> properties) {

        return properties.stream()
                .map(prop -> prop.get("property") + ":" + prop.get("type"))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    @Override
    public void close() {

        this.driver.close();
    }
}
