package dev.langchain4j.rag.content.retriever.neo4j;

import dev.langchain4j.internal.ValidationUtils;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.summary.ResultSummary;

import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;

public class Neo4jGraph implements AutoCloseable {

    public static class Builder {
        private Driver driver;
        private Long sample;
        private Long maxRels;

        /**
         * @param driver the {@link Driver} (required)
         */
        Builder driver(Driver driver) {
            this.driver = driver;
            return this;
        }

        /**
         * @param sample number of nodes to sample per label with the `apoc.meta.data` procedure (default: 1000)
         */
        Builder sample(Long sample) {
            this.sample = sample;
            return this;
        }

        /**
         * @param maxRels the maximum number of relationships to look at per Node Label with the `apoc.meta.data` procedure (default: 100)
         */
        Builder maxRels(Long maxRels) {
            this.maxRels = maxRels;
            return this;
        }

        Neo4jGraph build() {
            return new Neo4jGraph(driver, sample, maxRels);
        }
    }

    private final static String SCHEMA_FROM_META_DATA = """
            CALL apoc.meta.data({maxRels: $maxRels, sample: $sample})
            YIELD label, other, elementType, type, property
            WITH label, elementType,
                 apoc.text.join(collect(case when NOT type = "RELATIONSHIP" then property+": "+type else null end),", ") AS properties,
                 collect(case when type = "RELATIONSHIP" AND elementType = "node" then "(:" + label + ")-[:" + property + "]->(:" + toString(other[0]) + ")" else null end) as patterns
            with  elementType as type,
            apoc.text.join(collect(":"+label+" {"+properties+"}"),"\\n") as entities, apoc.text.join(apoc.coll.flatten(collect(coalesce(patterns,[]))),"\\n") as patterns
            return collect(case type when "relationship" then entities end)[0] as relationships,
                collect(case type when "node" then entities end)[0] as nodes,
                collect(case type when "node" then patterns end)[0] as patterns
            """;

    private final Driver driver;
    private final Long sample;
    private final Long maxRels;

    private String schema;

    public Neo4jGraph(final Driver driver, Long sample, Long maxRels) {

        this.sample = getOrDefault(sample, 1000L);
        this.maxRels = getOrDefault(maxRels, 100L);
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
        return executeRead(queryString, Map.of());
    }
    
    public List<Record> executeRead(String queryString, Map<String, Object> parameters) {

        return this.driver.executableQuery(queryString).withParameters(parameters).execute().records();
    }

    public void refreshSchema() {
        final Record record = executeRead(SCHEMA_FROM_META_DATA, Map.of("sample", sample, "maxRels", maxRels))
                .get(0);
        final String nodesString = record.get("nodes").asString();
        final String relationshipsString = record.get("relationships").asString();
        final String patternsString = record.get("patterns").asString();
        this.schema = "Node properties are the following:\n" + nodesString
                + "\n\n" + "Relationship properties are the following:\n"
                + relationshipsString
                + "\n\n" + "The relationships are the following:\n"
                + patternsString;
    }

    @Override
    public void close() {

        this.driver.close();
    }
}
