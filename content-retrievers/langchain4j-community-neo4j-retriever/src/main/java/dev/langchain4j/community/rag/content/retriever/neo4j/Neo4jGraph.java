package dev.langchain4j.community.rag.content.retriever.neo4j;

import static dev.langchain4j.community.rag.content.retriever.neo4j.Neo4jGraphSchemaUtils.getSchemaFromMetadata;
import static dev.langchain4j.internal.Utils.getOrDefault;

import dev.langchain4j.internal.ValidationUtils;
import java.util.List;
import java.util.Map;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.exceptions.ClientException;
import org.neo4j.driver.summary.ResultSummary;

public class Neo4jGraph implements AutoCloseable {

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

        public Neo4jGraph build() {
            return new Neo4jGraph(driver, sample, maxRels);
        }
    }

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

    public static Builder builder() {
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
}
