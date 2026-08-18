package dev.langchain4j.community.store.embedding.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

class DynamoDbAttributeCodecTest {

    @Test
    void should_round_trip_string() {
        assertRoundTrip("hello", "hello");
    }

    @Test
    void should_round_trip_integer_as_native_number() {
        AttributeValue encoded = DynamoDbAttributeCodec.encode(42);
        assertThat(encoded.n()).isEqualTo("42");
        assertThat(DynamoDbAttributeCodec.decode(encoded)).isEqualTo(42);
    }

    @Test
    void should_round_trip_long_as_native_number() {
        long value = 9_000_000_000L;
        AttributeValue encoded = DynamoDbAttributeCodec.encode(value);
        assertThat(encoded.n()).isEqualTo("9000000000");
        assertThat(DynamoDbAttributeCodec.decode(encoded)).isEqualTo(value);
    }

    @Test
    void should_round_trip_boolean() {
        AttributeValue encoded = DynamoDbAttributeCodec.encode(true);
        assertThat(encoded.bool()).isTrue();
        assertThat(DynamoDbAttributeCodec.decode(encoded)).isEqualTo(true);
    }

    @Test
    void should_round_trip_float_as_tagged_string() {
        AttributeValue encoded = DynamoDbAttributeCodec.encode(1.5f);
        assertThat(encoded.n()).isNull();
        assertThat(DynamoDbAttributeCodec.decode(encoded)).isEqualTo(1.5f);
    }

    @Test
    void should_round_trip_subnormal_double_that_dynamodb_number_cannot_store() {
        // Double.MIN_VALUE underflows DynamoDB's Number type; the tagged-string encoding preserves it.
        AttributeValue encoded = DynamoDbAttributeCodec.encode(Double.MIN_VALUE);
        assertThat(encoded.n()).isNull();
        assertThat(DynamoDbAttributeCodec.decode(encoded)).isEqualTo(Double.MIN_VALUE);
    }

    @Test
    void should_round_trip_float_min_value() {
        AttributeValue encoded = DynamoDbAttributeCodec.encode(Float.MIN_VALUE);
        assertThat(DynamoDbAttributeCodec.decode(encoded)).isEqualTo(Float.MIN_VALUE);
    }

    @Test
    void should_escape_plain_string_that_collides_with_float_tag() {
        String tricky = DynamoDbAttributeCodec.FLOAT_PREFIX + "1.5";
        assertRoundTrip(tricky, tricky);
    }

    @Test
    void should_escape_plain_string_that_collides_with_string_tag() {
        String tricky = DynamoDbAttributeCodec.STRING_PREFIX + "abc";
        assertRoundTrip(tricky, tricky);
    }

    private static void assertRoundTrip(String input, String expected) {
        AttributeValue encoded = DynamoDbAttributeCodec.encode(input);
        assertThat(DynamoDbAttributeCodec.decode(encoded)).isEqualTo(expected);
    }
}
