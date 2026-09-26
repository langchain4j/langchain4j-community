package dev.langchain4j.community.store.embedding.dynamodb;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;
import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.VectorDistanceFunction;

@EnabledIfEnvironmentVariable(named = "AWS_INTEGRATION_TESTS", matches = "(?i)true")
class DynamoDbEmbeddingStoreInlineFilterIT {

    private static final String TEST_TABLE_PREFIX = "langchain4j-test-inline-";
    private static final String TEST_INDEX_PREFIX = "test-index-inline-";

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

        Map<String, ScalarAttributeType> inlineFilters = new LinkedHashMap<>();
        inlineFilters.put("ownerId", ScalarAttributeType.S);
        inlineFilters.put("tenant", ScalarAttributeType.N);

        embeddingStore = DynamoDbEmbeddingStore.builder()
                .tableName(testTableName)
                .indexName(testIndexName)
                .region(region)
                .distanceFunction(VectorDistanceFunction.COSINE)
                .createTableIfNotExists(true)
                .inlineFilterAttributes(inlineFilters)
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

    @Test
    void should_create_table_with_inline_filters_and_filter_by_string_and_number() throws InterruptedException {
        TextSegment ownerOneTenantOne =
                TextSegment.from("content for owner one", new Metadata(Map.of("ownerId", "user-1", "tenant", 1)));
        TextSegment ownerTwoTenantTwo =
                TextSegment.from("content for owner two", new Metadata(Map.of("ownerId", "user-2", "tenant", 2)));

        embeddingStore.add(embeddingModel.embed(ownerOneTenantOne).content(), ownerOneTenantOne);
        embeddingStore.add(embeddingModel.embed(ownerTwoTenantTwo).content(), ownerTwoTenantTwo);

        Embedding query = embeddingModel.embed("content").content();

        List<String> ownerOneTexts =
                eventualSearchTexts(query, metadataKey("ownerId").isEqualTo("user-1"));
        assertThat(ownerOneTexts).containsExactly("content for owner one");

        List<String> ownerTwoTexts =
                eventualSearchTexts(query, metadataKey("ownerId").isEqualTo("user-2"));
        assertThat(ownerTwoTexts).containsExactly("content for owner two");

        List<String> tenantOneTexts =
                eventualSearchTexts(query, metadataKey("tenant").isEqualTo(1));
        assertThat(tenantOneTexts).containsExactly("content for owner one");

        List<String> tenantTwoTexts =
                eventualSearchTexts(query, metadataKey("tenant").isEqualTo(2));
        assertThat(tenantTwoTexts).containsExactly("content for owner two");
    }

    private static List<String> eventualSearchTexts(Embedding query, Filter filter) throws InterruptedException {
        List<String> texts = List.of();
        for (int attempt = 0; attempt < 30; attempt++) {
            texts = searchTexts(query, filter);
            if (!texts.isEmpty()) {
                return texts;
            }
            Thread.sleep(2000);
        }
        return texts;
    }

    private static List<String> searchTexts(Embedding query, Filter filter) {
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(query)
                .maxResults(10)
                .filter(filter)
                .build());
        return result.matches().stream().map(match -> match.embedded().text()).toList();
    }
}
