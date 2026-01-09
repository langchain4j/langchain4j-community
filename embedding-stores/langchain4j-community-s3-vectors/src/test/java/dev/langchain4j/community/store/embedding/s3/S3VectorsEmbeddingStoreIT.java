package dev.langchain4j.community.store.embedding.s3;

import static org.assertj.core.data.Percentage.withPercentage;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIT;
import java.util.UUID;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.CreateVectorBucketRequest;
import software.amazon.awssdk.services.s3vectors.model.DeleteIndexRequest;
import software.amazon.awssdk.services.s3vectors.model.DeleteVectorBucketRequest;
import software.amazon.awssdk.services.s3vectors.model.DistanceMetric;

@EnabledIfEnvironmentVariable(named = "AWS_SECRET_ACCESS_KEY", matches = ".+")
class S3VectorsEmbeddingStoreIT extends EmbeddingStoreIT {

    private static final String TEST_BUCKET_PREFIX = "langchain4j-test-";
    private static final String TEST_INDEX_PREFIX = "test-index-";

    private static S3VectorsEmbeddingStore embeddingStore;
    private static S3VectorsClient s3VectorsClient;
    private static String testBucketName;
    private static String testIndexName;
    private static boolean bucketCreatedByTest = false;

    private static final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void beforeAll() {
        testBucketName = System.getenv("S3_VECTORS_BUCKET_NAME");
        if (testBucketName == null || testBucketName.isBlank()) {
            testBucketName = TEST_BUCKET_PREFIX + UUID.randomUUID().toString().substring(0, 8);
            bucketCreatedByTest = true;
        }

        testIndexName = TEST_INDEX_PREFIX + UUID.randomUUID().toString().substring(0, 8);

        String region = System.getenv("AWS_REGION");
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }

        s3VectorsClient = S3VectorsClient.builder().region(Region.of(region)).build();

        if (bucketCreatedByTest) {
            try {
                s3VectorsClient.createVectorBucket(CreateVectorBucketRequest.builder()
                        .vectorBucketName(testBucketName)
                        .build());
            } catch (Exception e) {
                // bucket may already exist
            }
        }

        embeddingStore = S3VectorsEmbeddingStore.builder()
                .vectorBucketName(testBucketName)
                .indexName(testIndexName)
                .region(region)
                .distanceMetric(DistanceMetric.COSINE)
                .createIndexIfNotExists(true)
                .build();
    }

    @AfterAll
    static void afterAll() {
        if (s3VectorsClient != null) {
            try {
                s3VectorsClient.deleteIndex(DeleteIndexRequest.builder()
                        .vectorBucketName(testBucketName)
                        .indexName(testIndexName)
                        .build());
            } catch (Exception e) {
                // Cleanup may fail if resource doesn't exist
            }

            if (bucketCreatedByTest) {
                try {
                    s3VectorsClient.deleteVectorBucket(DeleteVectorBucketRequest.builder()
                            .vectorBucketName(testBucketName)
                            .build());
                } catch (Exception e) {
                    // Cleanup may fail if resource doesn't exist
                }
            }

            s3VectorsClient.close();
        }
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    @Override
    protected void clearStore() {
        embeddingStore.removeAll();
    }

    @Override
    protected boolean assertEmbedding() {
        // S3 Vectors does not return embedding vectors in query results
        return false;
    }

    @Override
    protected Percentage percentage() {
        return withPercentage(6);
    }

    @Override
    protected boolean testLong1746714878034235396() {
        return false;
    }
}
