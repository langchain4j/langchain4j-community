package dev.langchain4j.community.store.embedding.yugabytedb;

/**
 * Filter mapper for COLUMN_PER_KEY metadata storage mode.
 * <p>
 * Maps filters to SQL WHERE clauses that work with individual metadata columns.
 * Each metadata key has its own dedicated column with proper SQL typing.
 */
class ColumnFilterMapper extends YugabyteDBFilterMapper {

    @Override
    String formatKey(String key, Class<?> valueType) {
        return String.format("%s::%s", key, SQL_TYPE_MAP.get(valueType));
    }

    @Override
    String formatKeyAsString(String key) {
        return key;
    }
}
