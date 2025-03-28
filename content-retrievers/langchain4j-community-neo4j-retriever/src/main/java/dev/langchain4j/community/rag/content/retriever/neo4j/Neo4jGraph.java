package dev.langchain4j.community.rag.content.retriever.neo4j;

import static dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jGraphSchemaUtils.getSchemaFromMetadata;
import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.internal.ValidationUtils;
import java.util.List;
import java.util.Map;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.summary.ResultSummary;

public class Neo4jGraph implements AutoCloseable {
    public record GraphSchema(List<String> nodesProperties, List<String> relationshipsProperties, List<String> patterns) {}

    private final Driver driver;
    private final Long sample;
    private final Long maxRels;

    private String schema;
    private GraphSchema structuredSchema;
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
    
    public GraphSchema getStructuredSchema() {
        return structuredSchema;
    }
    
    public String getSchema() {
        return schema;
    }

    public void setStructuredSchema(final GraphSchema structuredSchema) {
        this.structuredSchema = structuredSchema;
    }

    public ResultSummary executeWrite(String queryString) {
        return executeWrite(queryString, Map.of());
    }

    public ResultSummary executeWrite(String queryString, Map<String, Object> params) {

        try (Session session = this.driver.session()) {
            return session.executeWrite(tx -> tx.run(queryString, params).consume());
        } catch (ClientException e) {
            throw new Neo4jException("Error executing query: " + queryString, e);
        }
    }

    public List<Record> executeRead(String queryString) {
        return executeRead(queryString, Map.of());
    }

    public List<Record> executeRead(String queryString, Map<String, Object> parameters) {

        return this.driver
                .executableQuery(queryString)
                .withParameters(parameters)
                .execute()
                .records();
    }

    public void refreshSchema() {
        this.schema = getSchemaFromMetadata(this, sample, maxRels);
    }

    @Override
    public void close() {
        this.driver.close();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Driver driver;
        private Long sample;
        private Long maxRels;

        /**
         * @param driver the {@link Driver} (required)
         */
        public Builder driver(Driver driver) {
            this.driver = driver;
            return this;
        }

        /**
         * @param sample number of nodes to sample per label with the `apoc.meta.data` procedure (default: 1000)
         */
        public Builder sample(Long sample) {
            this.sample = sample;
            return this;
        }

        /**
         * @param maxRels the maximum number of relationships to look at per Node Label with the `apoc.meta.data` procedure (default: 100)
         */
        public Builder maxRels(Long maxRels) {
            this.maxRels = maxRels;
            return this;
        }

        /**
         * Creates an instance a {@link Driver}, starting from uri, user and password
         *
         * @param uri      the Bolt URI to a Neo4j instance
         * @param user     the Neo4j instance's username
         * @param password the Neo4j instance's password
         */
        public Builder withBasicAuth(String uri, String user, String password) {
            this.driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
            return this;
        }

        public Neo4jGraph build() {
            return new Neo4jGraph(driver, sample, maxRels);
        }
    }
}
