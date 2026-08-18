package dev.langchain4j.community.store.embedding.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.comparison.IsEqualTo;
import dev.langchain4j.store.embedding.filter.comparison.IsGreaterThan;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Or;
import org.junit.jupiter.api.Test;

class DynamoDbMetadataFilterMapperTest {

    @Test
    void should_return_null_for_null_filter() {
        assertThat(DynamoDbMetadataFilterMapper.map(null)).isNull();
    }

    @Test
    void should_map_equal_with_string() {
        IsEqualTo filter = new IsEqualTo("genre", "scifi");

        DynamoDbMetadataFilterMapper.SearchCondition condition = DynamoDbMetadataFilterMapper.map(filter);

        assertThat(condition.expression).isEqualTo("#k0 = :v0");
        assertThat(condition.expressionAttributeNames).containsEntry("#k0", "genre");
        assertThat(condition.expressionAttributeValues.get(":v0").s()).isEqualTo("scifi");
    }

    @Test
    void should_map_equal_with_number() {
        IsEqualTo filter = new IsEqualTo("rating", 5);

        DynamoDbMetadataFilterMapper.SearchCondition condition = DynamoDbMetadataFilterMapper.map(filter);

        assertThat(condition.expression).isEqualTo("#k0 = :v0");
        assertThat(condition.expressionAttributeNames).containsEntry("#k0", "rating");
        assertThat(condition.expressionAttributeValues.get(":v0").n()).isEqualTo("5");
    }

    @Test
    void should_map_equal_with_boolean() {
        IsEqualTo filter = new IsEqualTo("active", true);

        DynamoDbMetadataFilterMapper.SearchCondition condition = DynamoDbMetadataFilterMapper.map(filter);

        assertThat(condition.expressionAttributeValues.get(":v0").bool()).isTrue();
    }

    @Test
    void should_encode_float_filter_as_tagged_string_matching_storage() {
        // Floats are persisted as tagged strings (DynamoDB Number underflows on subnormals), so a
        // filter value must be encoded the same way or it would never match the stored value.
        IsEqualTo filter = new IsEqualTo("score", 1.5f);

        DynamoDbMetadataFilterMapper.SearchCondition condition = DynamoDbMetadataFilterMapper.map(filter);

        assertThat(condition.expressionAttributeValues.get(":v0").n()).isNull();
        assertThat(condition.expressionAttributeValues.get(":v0").s())
                .isEqualTo(DynamoDbAttributeCodec.FLOAT_PREFIX + "1.5");
    }

    @Test
    void should_encode_double_filter_as_tagged_string_matching_storage() {
        IsEqualTo filter = new IsEqualTo("score", 1.5d);

        DynamoDbMetadataFilterMapper.SearchCondition condition = DynamoDbMetadataFilterMapper.map(filter);

        assertThat(condition.expressionAttributeValues.get(":v0").n()).isNull();
        assertThat(condition.expressionAttributeValues.get(":v0").s())
                .isEqualTo(DynamoDbAttributeCodec.DOUBLE_PREFIX + "1.5");
    }

    @Test
    void should_map_and_of_equals() {
        And filter = new And(new IsEqualTo("genre", "scifi"), new IsEqualTo("year", 2020));

        DynamoDbMetadataFilterMapper.SearchCondition condition = DynamoDbMetadataFilterMapper.map(filter);

        assertThat(condition.expression).isEqualTo("#k0 = :v0 AND #k1 = :v1");
        assertThat(condition.expressionAttributeNames)
                .containsEntry("#k0", "genre")
                .containsEntry("#k1", "year");
        assertThat(condition.expressionAttributeValues.get(":v0").s()).isEqualTo("scifi");
        assertThat(condition.expressionAttributeValues.get(":v1").n()).isEqualTo("2020");
    }

    @Test
    void should_map_nested_and() {
        And filter = new And(new IsEqualTo("a", "1"), new And(new IsEqualTo("b", "2"), new IsEqualTo("c", "3")));

        DynamoDbMetadataFilterMapper.SearchCondition condition = DynamoDbMetadataFilterMapper.map(filter);

        assertThat(condition.expression).isEqualTo("#k0 = :v0 AND #k1 = :v1 AND #k2 = :v2");
        assertThat(condition.expressionAttributeNames).hasSize(3);
        assertThat(condition.expressionAttributeValues).hasSize(3);
    }

    @Test
    void should_throw_for_greater_than() {
        IsGreaterThan filter = new IsGreaterThan("rating", 4);

        assertThatThrownBy(() -> DynamoDbMetadataFilterMapper.map(filter))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("only supports equality");
    }

    @Test
    void should_throw_for_or() {
        Or filter = new Or(new IsEqualTo("genre", "scifi"), new IsEqualTo("genre", "fantasy"));

        assertThatThrownBy(() -> DynamoDbMetadataFilterMapper.map(filter))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("only supports equality");
    }

    @Test
    void should_throw_for_unsupported_filter_type() {
        Filter customFilter = object -> false;

        assertThatThrownBy(() -> DynamoDbMetadataFilterMapper.map(customFilter))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("Unsupported filter type");
    }
}
