package dev.langchain4j.community.store.embedding.redis;

import dev.langchain4j.community.store.filter.RedisFilterExpression;
import dev.langchain4j.store.embedding.filter.Filter;
import java.util.Map;
import redis.clients.jedis.search.schemafields.SchemaField;

/**
 * Enhanced Redis metadata filter mapper that supports the advanced Redis-specific filters.
 * This mapper extends the standard RedisMetadataFilterMapper to handle the enhanced
 * Redis filter expressions.
 */
public class EnhancedRedisMetadataFilterMapper extends RedisMetadataFilterMapper {

    /**
     * Creates a new EnhancedRedisMetadataFilterMapper with the specified schema field map.
     *
     * @param schemaFieldMap The schema field map
     */
    public EnhancedRedisMetadataFilterMapper(Map<String, SchemaField> schemaFieldMap) {
        super(schemaFieldMap);
    }

    /**
     * Maps a filter to a Redis filter string. This implementation handles both
     * standard LangChain4j filters and enhanced Redis-specific filters.
     *
     * @param filter The filter to map
     * @return The Redis filter string
     */
    @Override
    public String mapToFilter(Filter filter) {
        // If it's our enhanced filter, use its direct Redis syntax
        if (filter instanceof RedisFilterExpression) {
            return ((RedisFilterExpression) filter).toRedisQueryString();
        }

        // Otherwise, fall back to standard mapping
        return super.mapToFilter(filter);
    }
}
