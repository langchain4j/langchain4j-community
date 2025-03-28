package dev.langchain4j.community.neo4j.spring;

import static dev.langchain4j.community.neo4j.spring.Neo4jEmbeddingStoreProperties.PREFIX;

import org.neo4j.driver.Driver;
import org.neo4j.driver.SessionConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = PREFIX)
public class Neo4jEmbeddingStoreProperties {

    static final String PREFIX = "langchain4j.community.neo4j";

    private String indexName;
    private String metadataPrefix;
    private String embeddingProperty;
    private String idProperty;
    private String label;
    private String textProperty;
    private String databaseName;
    private String retrievalQuery;
    private SessionConfig config;
    private Driver driver;
    private int dimension;
    private long awaitIndexTimeout;

    @NestedConfigurationProperty
    private BasicAuth auth;

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(final String indexName) {
        this.indexName = indexName;
    }

    public String getMetadataPrefix() {
        return metadataPrefix;
    }

    public void setMetadataPrefix(final String metadataPrefix) {
        this.metadataPrefix = metadataPrefix;
    }

    public String getEmbeddingProperty() {
        return embeddingProperty;
    }

    public void setEmbeddingProperty(final String embeddingProperty) {
        this.embeddingProperty = embeddingProperty;
    }

    public String getIdProperty() {
        return idProperty;
    }

    public void setIdProperty(final String idProperty) {
        this.idProperty = idProperty;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(final String label) {
        this.label = label;
    }

    public String getTextProperty() {
        return textProperty;
    }

    public void setTextProperty(final String textProperty) {
        this.textProperty = textProperty;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(final String databaseName) {
        this.databaseName = databaseName;
    }

    public String getRetrievalQuery() {
        return retrievalQuery;
    }

    public void setRetrievalQuery(final String retrievalQuery) {
        this.retrievalQuery = retrievalQuery;
    }

    public SessionConfig getConfig() {
        return config;
    }

    public void setConfig(final SessionConfig config) {
        this.config = config;
    }

    public Driver getDriver() {
        return driver;
    }

    public void setDriver(final Driver driver) {
        this.driver = driver;
    }

    public int getDimension() {
        return dimension;
    }

    public void setDimension(final int dimension) {
        this.dimension = dimension;
    }

    public long getAwaitIndexTimeout() {
        return awaitIndexTimeout;
    }

    public void setAwaitIndexTimeout(final long awaitIndexTimeout) {
        this.awaitIndexTimeout = awaitIndexTimeout;
    }

    public BasicAuth getAuth() {
        return auth;
    }

    public void setAuth(final BasicAuth auth) {
        this.auth = auth;
    }

    public static class BasicAuth {

        private String uri;
        private String user;
        private String password;

        public String getUri() {
            return uri;
        }

        public void setUri(final String uri) {
            this.uri = uri;
        }

        public String getUser() {
            return user;
        }

        public void setUser(final String user) {
            this.user = user;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(final String password) {
            this.password = password;
        }
    }
}
