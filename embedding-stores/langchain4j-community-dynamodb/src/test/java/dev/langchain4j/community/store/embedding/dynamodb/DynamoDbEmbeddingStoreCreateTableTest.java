package dev.langchain4j.community.store.embedding.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.CreateTableResponse;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;

class DynamoDbEmbeddingStoreCreateTableTest {

    @Test
    void should_declare_attribute_definitions_for_string_inline_filters() {
        CapturingDynamoDbClient client = new CapturingDynamoDbClient();
        DynamoDbEmbeddingStore store = DynamoDbEmbeddingStore.builder()
                .dynamoDbClient(client)
                .tableName("t")
                .indexName("i")
                .inlineFilterAttributes(List.of("ownerId"))
                .build();

        store.add(embedding(), TextSegment.from("hello", Metadata.from("ownerId", "user-1")));

        CreateTableRequest request = client.createTableRequest.get();
        assertThat(request).isNotNull();
        assertThat(request.attributeDefinitions())
                .extracting(AttributeDefinition::attributeName)
                .contains("id", "ownerId");
        assertThat(typeOf(request, "ownerId")).isEqualTo(ScalarAttributeType.S);
    }

    @Test
    void should_declare_typed_attribute_definitions_for_string_and_number_inline_filters() {
        CapturingDynamoDbClient client = new CapturingDynamoDbClient();
        Map<String, ScalarAttributeType> types = new LinkedHashMap<>();
        types.put("ownerId", ScalarAttributeType.S);
        types.put("tenant", ScalarAttributeType.N);
        DynamoDbEmbeddingStore store = DynamoDbEmbeddingStore.builder()
                .dynamoDbClient(client)
                .tableName("t")
                .indexName("i")
                .inlineFilterAttributes(types)
                .build();

        store.add(embedding(), TextSegment.from("hello", new Metadata(Map.of("ownerId", "user-1", "tenant", 42))));

        CreateTableRequest request = client.createTableRequest.get();
        assertThat(typeOf(request, "ownerId")).isEqualTo(ScalarAttributeType.S);
        assertThat(typeOf(request, "tenant")).isEqualTo(ScalarAttributeType.N);
    }

    @Test
    void should_reject_boolean_inline_filter_at_build() {
        assertThatThrownBy(() -> DynamoDbEmbeddingStore.builder()
                        .dynamoDbClient(new CapturingDynamoDbClient())
                        .tableName("t")
                        .indexName("i")
                        .inlineFilterAttributes(Map.of("flag", ScalarAttributeType.B))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("flag")
                .hasMessageContaining("S")
                .hasMessageContaining("N");
    }

    @Test
    void should_reject_inline_filter_that_collides_with_key_attribute() {
        assertThatThrownBy(() -> DynamoDbEmbeddingStore.builder()
                        .dynamoDbClient(new CapturingDynamoDbClient())
                        .tableName("t")
                        .indexName("i")
                        .inlineFilterAttributes(List.of("id"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id")
                .hasMessageContaining("partition key");
    }

    @Test
    void should_throw_when_written_value_type_mismatches_declared_inline_filter_type() {
        CapturingDynamoDbClient client = new CapturingDynamoDbClient();
        DynamoDbEmbeddingStore store = DynamoDbEmbeddingStore.builder()
                .dynamoDbClient(client)
                .tableName("t")
                .indexName("i")
                .inlineFilterAttributes(Map.of("tenant", ScalarAttributeType.N))
                .build();

        assertThatThrownBy(() ->
                        store.add(embedding(), TextSegment.from("hello", Metadata.from("tenant", "not-a-number"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenant")
                .hasMessageContaining("N")
                .hasMessageContaining("S");
    }

    private static ScalarAttributeType typeOf(CreateTableRequest request, String attribute) {
        return request.attributeDefinitions().stream()
                .filter(d -> d.attributeName().equals(attribute))
                .map(AttributeDefinition::attributeType)
                .findFirst()
                .orElse(null);
    }

    private static Embedding embedding() {
        return new Embedding(new float[] {0.1f, 0.2f, 0.3f});
    }

    private static final class CapturingDynamoDbClient implements DynamoDbClient {

        private final AtomicReference<CreateTableRequest> createTableRequest = new AtomicReference<>();
        private boolean tableCreated;

        @Override
        public CreateTableResponse createTable(CreateTableRequest request) {
            createTableRequest.set(request);
            tableCreated = true;
            return CreateTableResponse.builder().build();
        }

        @Override
        public DescribeTableResponse describeTable(DescribeTableRequest request) {
            if (!tableCreated) {
                throw ResourceNotFoundException.builder().message("not found").build();
            }
            return DescribeTableResponse.builder()
                    .table(TableDescription.builder()
                            .tableStatus(TableStatus.ACTIVE)
                            .build())
                    .build();
        }

        @Override
        public BatchWriteItemResponse batchWriteItem(BatchWriteItemRequest request) {
            return BatchWriteItemResponse.builder().build();
        }

        @Override
        public String serviceName() {
            return "dynamodb";
        }

        @Override
        public void close() {}
    }
}
