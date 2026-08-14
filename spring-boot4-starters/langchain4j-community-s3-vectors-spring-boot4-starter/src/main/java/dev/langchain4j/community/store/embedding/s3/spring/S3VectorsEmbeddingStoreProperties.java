package dev.langchain4j.community.store.embedding.s3.spring;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import software.amazon.awssdk.services.s3vectors.model.DistanceMetric;

@ConfigurationProperties(prefix = S3VectorsEmbeddingStoreProperties.CONFIG_PREFIX)
public class S3VectorsEmbeddingStoreProperties {

    static final String CONFIG_PREFIX = "langchain4j.community.s3-vectors";

    private String vectorBucketName;
    private String indexName;
    private String region;
    private DistanceMetric distanceMetric;
    private Boolean createIndexIfNotExists;
    private String textMetadataKey;
    private Duration timeout;

    public String getVectorBucketName() {
        return vectorBucketName;
    }

    public void setVectorBucketName(String vectorBucketName) {
        this.vectorBucketName = vectorBucketName;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public DistanceMetric getDistanceMetric() {
        return distanceMetric;
    }

    public void setDistanceMetric(DistanceMetric distanceMetric) {
        this.distanceMetric = distanceMetric;
    }

    public Boolean getCreateIndexIfNotExists() {
        return createIndexIfNotExists;
    }

    public void setCreateIndexIfNotExists(Boolean createIndexIfNotExists) {
        this.createIndexIfNotExists = createIndexIfNotExists;
    }

    public String getTextMetadataKey() {
        return textMetadataKey;
    }

    public void setTextMetadataKey(String textMetadataKey) {
        this.textMetadataKey = textMetadataKey;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
