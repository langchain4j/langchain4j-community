package dev.langchain4j.community.store.embedding.dynamodb;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Encodes langchain4j metadata values to DynamoDB {@link AttributeValue}s and back.
 *
 * <p>Integer and long values map to native DynamoDB numbers ({@code N}). Float and double values are
 * stored as tagged strings instead, because DynamoDB's Number type cannot represent subnormal
 * magnitudes (smaller than roughly {@code 1E-130}) and would reject values such as
 * {@link Double#MIN_VALUE}. Encoding them as strings preserves the exact value and Java type on
 * read-back. Plain string values that happen to begin with one of the tags are escaped so they never
 * collide with an encoded number.
 *
 * <p>This single codec is used both when writing metadata and when building filter expressions, so a
 * filter on a float/double attribute compares against the same tagged-string representation that was
 * persisted.
 */
final class DynamoDbAttributeCodec {

    static final String FLOAT_PREFIX = " F#";
    static final String DOUBLE_PREFIX = " D#";
    static final String STRING_PREFIX = " S#";

    private DynamoDbAttributeCodec() {
        // utility class
    }

    /** Encodes a metadata value for storage or for a filter comparison. */
    static AttributeValue encode(Object value) {
        if (value instanceof String string) {
            return AttributeValue.fromS(encodeString(string));
        } else if (value instanceof Boolean b) {
            return AttributeValue.fromBool(b);
        } else if (value instanceof Float f) {
            return AttributeValue.fromS(FLOAT_PREFIX + f);
        } else if (value instanceof Double d) {
            return AttributeValue.fromS(DOUBLE_PREFIX + d);
        } else if (value instanceof Number number) {
            // Integer and Long fit DynamoDB's Number range exactly.
            return AttributeValue.fromN(number.toString());
        } else {
            return AttributeValue.fromS(encodeString(String.valueOf(value)));
        }
    }

    /** Decodes a stored {@link AttributeValue} back to a metadata value, or {@code null} if unsupported. */
    static Object decode(AttributeValue value) {
        if (value.s() != null) {
            return decodeString(value.s());
        } else if (value.n() != null) {
            return parseNumber(value.n());
        } else if (value.bool() != null) {
            return value.bool();
        }
        return null;
    }

    /** Escapes a plain string that would otherwise be mistaken for a tagged number. */
    private static String encodeString(String value) {
        if (value.startsWith(FLOAT_PREFIX) || value.startsWith(DOUBLE_PREFIX) || value.startsWith(STRING_PREFIX)) {
            return STRING_PREFIX + value;
        }
        return value;
    }

    private static Object decodeString(String value) {
        if (value.startsWith(FLOAT_PREFIX)) {
            return Float.parseFloat(value.substring(FLOAT_PREFIX.length()));
        }
        if (value.startsWith(DOUBLE_PREFIX)) {
            return Double.parseDouble(value.substring(DOUBLE_PREFIX.length()));
        }
        if (value.startsWith(STRING_PREFIX)) {
            return value.substring(STRING_PREFIX.length());
        }
        return value;
    }

    private static Object parseNumber(String number) {
        // Metadata numbers in langchain4j are Integer/Long/Float/Double; preserve integrality.
        if (number.contains(".") || number.contains("e") || number.contains("E")) {
            return Double.parseDouble(number);
        }
        try {
            long value = Long.parseLong(number);
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
            return value;
        } catch (NumberFormatException e) {
            return Double.parseDouble(number);
        }
    }
}
