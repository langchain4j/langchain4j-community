package dev.langchain4j.community.store.embedding.dynamodb;

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
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.VectorDistanceFunction;

@EnabledIfEnvironmentVariable(named = "AWS_INTEGRATION_TESTS", matches = "(?i)true")
class DynamoDbEmbeddingStoreIT extends EmbeddingStoreIT {

    private static final String TEST_TABLE_PREFIX = "langchain4j-test-";
    private static final String TEST_INDEX_PREFIX = "test-index-";

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
        // The store does not return embedding vectors in search results.
        return false;
    }

    @Override
    protected Percentage percentage() {
        return withPercentage(6);
    }
}
