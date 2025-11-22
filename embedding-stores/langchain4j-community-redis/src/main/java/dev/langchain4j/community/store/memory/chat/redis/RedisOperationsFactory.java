package dev.langchain4j.community.store.memory.chat.redis;

import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.json.JsonObjectMapper;

/**
 * Factory class used to generate the RedisOperations class
 * Returns the corresponding Redis operation class based on the StoreType
 */
public class RedisOperationsFactory {

    /**
     *
     * @param storeType The data structure used to storage the data.
     * @param client Redis client for database operations.
     * @param jsonMapper JSON object mapper for serializing and deserializing chat messages.
     * @return An appropriate RedisOperations implementation
     * @throws IllegalArgumentException if the storage mode is not supported
     */
    public static RedisOperations createRedisOperations(
            StoreType storeType, UnifiedJedis client, JsonObjectMapper jsonMapper) {
        return switch (storeType) {
            case JSON -> new JSONRedisOperations(client, jsonMapper);
            case STRING -> new StringRedisOperations(client);
            default -> throw new IllegalArgumentException("Unsupported store type: " + storeType);
        };
    }
}
