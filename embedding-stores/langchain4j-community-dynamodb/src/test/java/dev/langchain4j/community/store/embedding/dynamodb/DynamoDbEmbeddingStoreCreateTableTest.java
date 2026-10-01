package dev.langchain4j.community.store.embedding.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemResponse;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.CreateTableResponse;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;
import software.amazon.awssdk.services.dynamodb.model.DescribeTimeToLiveRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTimeToLiveResponse;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.SearchResultItem;
import software.amazon.awssdk.services.dynamodb.model.SearchVectorsRequest;
import software.amazon.awssdk.services.dynamodb.model.SearchVectorsResponse;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;
import software.amazon.awssdk.services.dynamodb.model.TableStatus;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveDescription;
import software.amazon.awssdk.services.dynamodb.model.TimeToLiveStatus;
import software.amazon.awssdk.services.dynamodb.model.UpdateTimeToLiveRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateTimeToLiveResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

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
    void should_reject_binary_inline_filter_at_build() {
        assertThatThrownBy(() -> DynamoDbEmbeddingStore.builder()
                        .dynamoDbClient(new CapturingDynamoDbClient())
                        .tableName("t")
                        .indexName("i")
                        .inlineFilterAttributes(Map.of("payload", ScalarAttributeType.B))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("payload")
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

    @Test
    void should_not_write_expiresAt_or_enable_ttl_when_ttl_is_not_set() {
        CapturingDynamoDbClient client = new CapturingDynamoDbClient();
        DynamoDbEmbeddingStore store = DynamoDbEmbeddingStore.builder()
                .dynamoDbClient(client)
                .tableName("t")
                .indexName("i")
                .build();

        store.add(embedding(), TextSegment.from("hello"));

        assertThat(client.updateTimeToLiveRequest.get()).isNull();
        assertThat(client.lastWrittenItem.get()).doesNotContainKey("expiresAt");
    }

    @Test
    void should_enable_ttl_and_stamp_expiresAt_when_ttl_is_set() {
        CapturingDynamoDbClient client = new CapturingDynamoDbClient();
        DynamoDbEmbeddingStore store = DynamoDbEmbeddingStore.builder()
                .dynamoDbClient(client)
                .tableName("t")
                .indexName("i")
                .ttl(Duration.ofHours(1))
                .build();

        long before = Instant.now().getEpochSecond();
        store.add(embedding(), TextSegment.from("hello"));
        long after = Instant.now().getEpochSecond();

        UpdateTimeToLiveRequest ttlRequest = client.updateTimeToLiveRequest.get();
        assertThat(ttlRequest).isNotNull();
        assertThat(ttlRequest.timeToLiveSpecification().enabled()).isTrue();
        assertThat(ttlRequest.timeToLiveSpecification().attributeName()).isEqualTo("expiresAt");

        AttributeValue expiresAt = client.lastWrittenItem.get().get("expiresAt");
        assertThat(expiresAt).isNotNull();
        long value = Long.parseLong(expiresAt.n());
        assertThat(value).isBetween(before + 3600, after + 3600);
    }

    @Test
    void should_use_custom_ttl_attribute_name() {
        CapturingDynamoDbClient client = new CapturingDynamoDbClient();
        DynamoDbEmbeddingStore store = DynamoDbEmbeddingStore.builder()
                .dynamoDbClient(client)
                .tableName("t")
                .indexName("i")
                .ttl(Duration.ofHours(1))
                .ttlAttribute("ttl")
                .build();

        store.add(embedding(), TextSegment.from("hello"));

        assertThat(client.updateTimeToLiveRequest
                        .get()
                        .timeToLiveSpecification()
                        .attributeName())
                .isEqualTo("ttl");
        assertThat(client.lastWrittenItem.get()).containsKey("ttl").doesNotContainKey("expiresAt");
    }

    @Test
    void should_let_per_segment_metadata_override_the_store_wide_ttl() {
        CapturingDynamoDbClient client = new CapturingDynamoDbClient();
        DynamoDbEmbeddingStore store = DynamoDbEmbeddingStore.builder()
                .dynamoDbClient(client)
                .tableName("t")
                .indexName("i")
                .ttl(Duration.ofHours(1))
                .build();

        long explicitExpiry = 9999999999L;
        store.add(embedding(), TextSegment.from("hello", new Metadata(Map.of("expiresAt", explicitExpiry))));

        Map<String, AttributeValue> item = client.lastWrittenItem.get();
        assertThat(item.get("expiresAt").n()).isEqualTo(Long.toString(explicitExpiry));
    }

    @Test
    void should_not_call_updateTimeToLive_when_ttl_already_enabled() {
        CapturingDynamoDbClient client = new CapturingDynamoDbClient();
        client.timeToLiveStatus = TimeToLiveStatus.ENABLED;
        DynamoDbEmbeddingStore store = DynamoDbEmbeddingStore.builder()
                .dynamoDbClient(client)
                .tableName("t")
                .indexName("i")
                .ttl(Duration.ofHours(1))
                .build();

        store.add(embedding(), TextSegment.from("hello"));

        assertThat(client.updateTimeToLiveRequest.get()).isNull();
        assertThat(client.lastWrittenItem.get()).containsKey("expiresAt");
    }

    @Test
    void should_enable_ttl_and_expire_only_items_with_their_own_expiry_when_only_ttl_attribute_is_set() {
        CapturingDynamoDbClient client = new CapturingDynamoDbClient();
        DynamoDbEmbeddingStore store = DynamoDbEmbeddingStore.builder()
                .dynamoDbClient(client)
                .tableName("t")
                .indexName("i")
                .ttlAttribute("expiresAt")
                .build();

        store.add(embedding(), TextSegment.from("lasting"));
        assertThat(client.lastWrittenItem.get()).doesNotContainKey("expiresAt");

        long explicitExpiry = 9999999999L;
        store.add(embedding(), TextSegment.from("expiring", new Metadata(Map.of("expiresAt", explicitExpiry))));
        assertThat(client.lastWrittenItem.get().get("expiresAt").n()).isEqualTo(Long.toString(explicitExpiry));

        assertThat(client.updateTimeToLiveRequest
                        .get()
                        .timeToLiveSpecification()
                        .attributeName())
                .isEqualTo("expiresAt");
    }

    @Test
    void should_reject_non_positive_ttl() {
        assertThatThrownBy(() -> DynamoDbEmbeddingStore.builder()
                        .dynamoDbClient(new CapturingDynamoDbClient())
                        .tableName("t")
                        .indexName("i")
                        .ttl(Duration.ZERO)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void should_reject_ttl_attribute_that_collides_with_inline_filter() {
        assertThatThrownBy(() -> DynamoDbEmbeddingStore.builder()
                        .dynamoDbClient(new CapturingDynamoDbClient())
                        .tableName("t")
                        .indexName("i")
                        .ttl(Duration.ofHours(1))
                        .ttlAttribute("ownerId")
                        .inlineFilterAttributes(List.of("ownerId"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ownerId");
    }

    @Test
    void should_reject_inline_filter_that_collides_with_vector_attribute() {
        assertThatThrownBy(() -> DynamoDbEmbeddingStore.builder()
                        .dynamoDbClient(new CapturingDynamoDbClient())
                        .tableName("t")
                        .indexName("i")
                        .inlineFilterAttributes(List.of("embedding"))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embedding")
                .hasMessageContaining("vector");
    }

    @Test
    void should_enable_ttl_on_a_pre_existing_table_without_creating_it() {
        CapturingDynamoDbClient client = new CapturingDynamoDbClient();
        client.tableAlreadyExists = true;
        DynamoDbEmbeddingStore store = DynamoDbEmbeddingStore.builder()
                .dynamoDbClient(client)
                .tableName("t")
                .indexName("i")
                .ttlAttribute("expiresAt")
                .build();

        store.add(embedding(), TextSegment.from("hello"));

        assertThat(client.createTableRequest.get()).isNull();
        assertThat(client.updateTimeToLiveRequest.get()).isNotNull();
        assertThat(client.updateTimeToLiveRequest
                        .get()
                        .timeToLiveSpecification()
                        .attributeName())
                .isEqualTo("expiresAt");
    }

    @Test
    void should_treat_concurrent_ttl_enable_as_success() {
        CapturingDynamoDbClient client = new CapturingDynamoDbClient();
        // UpdateTimeToLive fails, but a re-describe shows TTL was enabled meanwhile on the same attribute.
        client.updateTimeToLiveError = (DynamoDbException)
                DynamoDbException.builder().message("already enabled").build();
        client.timeToLiveStatusOnReDescribe = TimeToLiveStatus.ENABLED;
        client.timeToLiveAttribute = "expiresAt";
        DynamoDbEmbeddingStore store = DynamoDbEmbeddingStore.builder()
                .dynamoDbClient(client)
                .tableName("t")
                .indexName("i")
                .ttlAttribute("expiresAt")
                .build();

        assertThatCode(() -> store.add(embedding(), TextSegment.from("hello"))).doesNotThrowAnyException();
        assertThat(client.updateTimeToLiveRequest.get()).isNotNull();
    }

    @Test
    void should_propagate_update_ttl_failure_when_ttl_still_disabled() {
        CapturingDynamoDbClient client = new CapturingDynamoDbClient();
        client.updateTimeToLiveError = (DynamoDbException)
                DynamoDbException.builder().message("access denied").build();
        client.timeToLiveStatus = TimeToLiveStatus.DISABLED;
        DynamoDbEmbeddingStore store = DynamoDbEmbeddingStore.builder()
                .dynamoDbClient(client)
                .tableName("t")
                .indexName("i")
                .ttlAttribute("expiresAt")
                .build();

        assertThatThrownBy(() -> store.add(embedding(), TextSegment.from("hello")))
                .isInstanceOf(DynamoDbException.class);
    }

    @Test
    void should_not_enable_ttl_in_unmanaged_mode() {
        CapturingDynamoDbClient client = new CapturingDynamoDbClient();
        client.tableAlreadyExists = true;
        DynamoDbEmbeddingStore store = DynamoDbEmbeddingStore.builder()
                .dynamoDbClient(client)
                .tableName("t")
                .indexName("i")
                .createTableIfNotExists(false)
                .ttlAttribute("expiresAt")
                .build();

        store.add(embedding(), TextSegment.from("hello"));

        assertThat(client.updateTimeToLiveRequest.get()).isNull();
        assertThat(client.lastWrittenItem.get()).doesNotContainKey("expiresAt");
    }

    @Test
    void should_reject_sub_second_ttl() {
        assertThatThrownBy(() -> DynamoDbEmbeddingStore.builder()
                        .dynamoDbClient(new CapturingDynamoDbClient())
                        .tableName("t")
                        .indexName("i")
                        .ttl(Duration.ofMillis(500))
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("second");
    }

    @Test
    void should_reject_ttl_metadata_that_looks_like_epoch_milliseconds() {
        CapturingDynamoDbClient client = new CapturingDynamoDbClient();
        DynamoDbEmbeddingStore store = DynamoDbEmbeddingStore.builder()
                .dynamoDbClient(client)
                .tableName("t")
                .indexName("i")
                .ttlAttribute("expiresAt")
                .build();

        long epochMillis = 32503680000000L;
        assertThatThrownBy(() -> store.add(
                        embedding(), TextSegment.from("hello", new Metadata(Map.of("expiresAt", epochMillis)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiresAt")
                .hasMessageContaining("epoch millisecond");
    }

    @Test
    void should_reject_existing_ttl_enabled_on_a_different_attribute() {
        CapturingDynamoDbClient client = new CapturingDynamoDbClient();
        client.timeToLiveStatus = TimeToLiveStatus.ENABLED;
        client.timeToLiveAttribute = "someOtherTtl";
        DynamoDbEmbeddingStore store = DynamoDbEmbeddingStore.builder()
                .dynamoDbClient(client)
                .tableName("t")
                .indexName("i")
                .ttlAttribute("expiresAt")
                .build();

        assertThatThrownBy(() -> store.add(embedding(), TextSegment.from("hello")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("someOtherTtl")
                .hasMessageContaining("expiresAt");
        assertThat(client.updateTimeToLiveRequest.get()).isNull();
    }

    @Test
    void should_drop_expired_matches_from_search_results() {
        long now = 1_000_000_000L;
        CapturingDynamoDbClient client = new CapturingDynamoDbClient();
        client.searchResultItems = List.of(
                itemWithExpiry("live", now + 3600), itemWithExpiry("dead", now - 1), itemWithoutExpiry("eternal"));
        DynamoDbEmbeddingStore store = DynamoDbEmbeddingStore.builder()
                .dynamoDbClient(client)
                .tableName("t")
                .indexName("i")
                .ttlAttribute("expiresAt")
                .clock(Clock.fixed(Instant.ofEpochSecond(now), ZoneOffset.UTC))
                .build();

        EmbeddingSearchResult<TextSegment> result = store.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(embedding())
                .maxResults(10)
                .build());

        assertThat(result.matches()).extracting(EmbeddingMatch::embeddingId).containsExactly("live", "eternal");
    }

    private static Map<String, AttributeValue> itemWithExpiry(String id, long expiresAt) {
        Map<String, AttributeValue> item = new LinkedHashMap<>();
        item.put("id", AttributeValue.fromS(id));
        item.put("_page_content", AttributeValue.fromS(id));
        item.put("expiresAt", AttributeValue.fromN(Long.toString(expiresAt)));
        return item;
    }

    private static Map<String, AttributeValue> itemWithoutExpiry(String id) {
        Map<String, AttributeValue> item = new LinkedHashMap<>();
        item.put("id", AttributeValue.fromS(id));
        item.put("_page_content", AttributeValue.fromS(id));
        return item;
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
        private final AtomicReference<UpdateTimeToLiveRequest> updateTimeToLiveRequest = new AtomicReference<>();
        private final AtomicReference<Map<String, AttributeValue>> lastWrittenItem = new AtomicReference<>();
        private boolean tableCreated;
        private boolean tableAlreadyExists;
        private TimeToLiveStatus timeToLiveStatus = TimeToLiveStatus.DISABLED;
        private String timeToLiveAttribute;
        private RuntimeException updateTimeToLiveError;
        private TimeToLiveStatus timeToLiveStatusOnReDescribe;
        private boolean timeToLiveDescribed;
        private List<Map<String, AttributeValue>> searchResultItems = List.of();

        @Override
        public CreateTableResponse createTable(CreateTableRequest request) {
            createTableRequest.set(request);
            tableCreated = true;
            return CreateTableResponse.builder().build();
        }

        @Override
        public DescribeTableResponse describeTable(DescribeTableRequest request) {
            if (!tableCreated && !tableAlreadyExists) {
                throw ResourceNotFoundException.builder().message("not found").build();
            }
            return DescribeTableResponse.builder()
                    .table(TableDescription.builder()
                            .tableStatus(TableStatus.ACTIVE)
                            .build())
                    .build();
        }

        @Override
        public DescribeTimeToLiveResponse describeTimeToLive(DescribeTimeToLiveRequest request) {
            // On the first describe, report the initial status; on any later describe (the re-probe after a failed
            // UpdateTimeToLive), report timeToLiveStatusOnReDescribe when set, simulating a concurrent enable.
            TimeToLiveStatus status = timeToLiveStatus;
            if (timeToLiveDescribed && timeToLiveStatusOnReDescribe != null) {
                status = timeToLiveStatusOnReDescribe;
            }
            timeToLiveDescribed = true;
            return DescribeTimeToLiveResponse.builder()
                    .timeToLiveDescription(TimeToLiveDescription.builder()
                            .timeToLiveStatus(status)
                            .attributeName(timeToLiveAttribute)
                            .build())
                    .build();
        }

        @Override
        public UpdateTimeToLiveResponse updateTimeToLive(UpdateTimeToLiveRequest request) {
            updateTimeToLiveRequest.set(request);
            if (updateTimeToLiveError != null) {
                throw updateTimeToLiveError;
            }
            timeToLiveStatus = TimeToLiveStatus.ENABLING;
            timeToLiveAttribute = request.timeToLiveSpecification().attributeName();
            return UpdateTimeToLiveResponse.builder().build();
        }

        @Override
        public BatchWriteItemResponse batchWriteItem(BatchWriteItemRequest request) {
            request.requestItems().values().forEach(writeRequests -> {
                for (WriteRequest writeRequest : writeRequests) {
                    PutRequest putRequest = writeRequest.putRequest();
                    if (putRequest != null) {
                        lastWrittenItem.set(putRequest.item());
                    }
                }
            });
            return BatchWriteItemResponse.builder().build();
        }

        @Override
        public SearchVectorsResponse searchVectors(SearchVectorsRequest request) {
            List<SearchResultItem> results = searchResultItems.stream()
                    .map(item ->
                            SearchResultItem.builder().item(item).score(0.0).build())
                    .toList();
            return SearchVectorsResponse.builder().searchResults(results).build();
        }

        @Override
        public String serviceName() {
            return "dynamodb";
        }

        @Override
        public void close() {}
    }
}
