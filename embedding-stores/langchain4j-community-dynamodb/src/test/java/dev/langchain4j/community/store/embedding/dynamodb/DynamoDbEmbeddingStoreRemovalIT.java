package dev.langchain4j.community.store.embedding.dynamodb;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreWithRemovalIT;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.VectorDistanceFunction;

@EnabledIfEnvironmentVariable(named = "AWS_INTEGRATION_TESTS", matches = "(?i)true")
class DynamoDbEmbeddingStoreRemovalIT extends EmbeddingStoreWithRemovalIT {

    private static final String TEST_TABLE_PREFIX = "langchain4j-removal-test-";
    private static final String TEST_INDEX_PREFIX = "removal-test-index-";

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
                .build();
    }

    @AfterAll
    static void afterAll() {
        if (dynamoDbClient != null) {
            try {
                dynamoDbClient.deleteTable(
                        DeleteTableRequest.builder().tableName(testTableName).build());
            } catch (Exception e) {
                // Cleanup may fail if the table does not exist
            }
            dynamoDbClient.close();
        }
    }

    @AfterEach
    void afterEach() {
        embeddingStore.removeAll();
    }

    @Override
    protected EmbeddingStore<TextSegment> embeddingStore() {
        return embeddingStore;
    }

    @Override
    protected EmbeddingModel embeddingModel() {
        return embeddingModel;
    }

    /**
     * DynamoDB vector search does not support filter-based deletion.
     * Only deletion by specific ids is supported.
     */
    @Override
    protected boolean supportsRemoveAllByFilter() {
        return false;
    }
}
