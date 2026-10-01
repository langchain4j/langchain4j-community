package dev.langchain4j.community.store.embedding.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTimeToLiveRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveStatus;
import software.amazon.awssdk.services.dynamodb.model.VectorDistanceFunction;

@EnabledIfEnvironmentVariable(named = "AWS_INTEGRATION_TESTS", matches = "(?i)true")
class DynamoDbEmbeddingStoreTtlIT {

    private static final String TEST_TABLE_PREFIX = "langchain4j-test-ttl-";
    private static final String TEST_INDEX_PREFIX = "test-index-ttl-";

    private static DynamoDbEmbeddingStore embeddingStore;
    private static DynamoDbClient dynamoDbClient;
    private static String testTableName;

    private static final EmbeddingModel embeddingModel = new AllMiniLmL6V2QuantizedEmbeddingModel();

    @BeforeAll
    static void beforeAll() {
        testTableName = TEST_TABLE_PREFIX + UUID.randomUUID().toString().substring(0, 8);
        String testIndexName = TEST_INDEX_PREFIX + UUID.randomUUID().toString().substring(0, 8);

        String region = System.getenv("AWS_REGION");
        if (region == null || region.isBlank()) {
            region = "us-east-1";
        }

        dynamoDbClient = DynamoDbClient.builder().region(Region.of(region)).build();

        embeddingStore = DynamoDbEmbeddingStore.builder()
                .tableName(testTableName)
                .indexName(testIndexName)
                .region(region)
                .distanceFunction(VectorDistanceFunction.COSINE)
                .createTableIfNotExists(true)
                .ttl(Duration.ofDays(1))
                .build();
    }

    @AfterAll
    static void afterAll() {
        if (dynamoDbClient != null) {
            try {
                dynamoDbClient.deleteTable(
                        DeleteTableRequest.builder().tableName(testTableName).build());
            } catch (ResourceNotFoundException e) {
                // Table was never created (e.g. test setup failed); nothing to clean up.
            } catch (RuntimeException e) {
                // Any other failure (access denied, throttling, network) leaves a live, vector-indexed table
                // behind; surface it so the leak is not silent.
                System.err.println("Failed to delete test table " + testTableName + ": " + e);
            }
            dynamoDbClient.close();
        }
    }

    @Test
    void should_enable_ttl_on_the_table_when_ttl_is_configured() {
        TextSegment segment = TextSegment.from("content with ttl");
        embeddingStore.add(embeddingModel.embed(segment).content(), segment);

        // DynamoDB reports ENABLED or ENABLING right after UpdateTimeToLive; propagation can take up to an hour.
        TimeToLiveStatus status = dynamoDbClient
                .describeTimeToLive(DescribeTimeToLiveRequest.builder()
                        .tableName(testTableName)
                        .build())
                .timeToLiveDescription()
                .timeToLiveStatus();

        assertThat(status).isIn(TimeToLiveStatus.ENABLED, TimeToLiveStatus.ENABLING);
    }
}
