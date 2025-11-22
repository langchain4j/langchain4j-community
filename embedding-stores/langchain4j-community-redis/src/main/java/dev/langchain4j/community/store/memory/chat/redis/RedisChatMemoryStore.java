package dev.langchain4j.community.store.memory.chat.redis;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.List;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.json.DefaultGsonObjectMapper;
import redis.clients.jedis.json.JsonObjectMapper;

/**
 * Implementation of {@link ChatMemoryStore} that stores chat messages in Redis.
 * Uses Jedis client to connect to Redis and manage message persistence.
 * <p>
 * Messages are stored as native JSON or JSON strings under keys derived from the memory ID.
 * Optional TTL (time-to-live) can be specified for automatic key expiration.
 */
public class RedisChatMemoryStore implements ChatMemoryStore {

    /**
     * Redis client for database operations.
     */
    private final UnifiedJedis client;

    /**
     * Prefix to be added to all Redis keys.
     */
    private final String keyPrefix;

    /**
     * Time-to-live value for Redis keys in seconds.
     * Keys will automatically expire after this duration.
     * A value of 0 or less means keys will not expire.
     */
    private final Long ttl;

    /**
     * JSON object mapper for serializing and deserializing chat messages.
     */
    private JsonObjectMapper jsonMapper;

    /**
     * Decide which data structure to use for storage and load
     */
    private final RedisOperations redisOperations;

    /**
     * Constructs a new Redis chat memory store with default prefix and TTL.
     *
     * @param host     Redis server hostname
     * @param port     Redis server port
     * @param user     Redis user (can be null for non-authenticated connections)
     * @param password Redis password (required if user is provided)
     */
    public RedisChatMemoryStore(String host, Integer port, String user, String password) {
        this(host, port, user, password, "", 0L, StoreType.JSON);
    }

    /**
     * Constructs a new Redis chat memory store with custom prefix and TTL and StoreType.
     *
     * @param host      Redis server hostname
     * @param port      Redis server port
     * @param user      Redis user (can be null for non-authenticated connections)
     * @param password  Redis password (required if user is provided)
     * @param prefix    Prefix for Redis keys (for namespacing)
     * @param ttl       Time-to-live value in seconds (≤0 means no expiration)
     * @param storeType Decide which type of RedisOperations to use(default use JSON)
     */
    public RedisChatMemoryStore(
            String host, Integer port, String user, String password, String prefix, Long ttl, StoreType storeType) {
        String finalHost = ensureNotBlank(host, "host");
        int finalPort = ensureNotNull(port, "port");
        if (user != null) {
            String finalUser = ensureNotBlank(user, "user");
            String finalPassword = ensureNotBlank(password, "password");
            JedisClientConfig jedisClientConfig = DefaultJedisClientConfig.builder()
                    .user(finalUser)
                    .password(finalPassword)
                    .build();
            this.client = new UnifiedJedis(new HostAndPort(finalHost, finalPort), jedisClientConfig);
        } else {
            this.client = new UnifiedJedis(new HostAndPort(finalHost, finalPort));
        }
        this.keyPrefix = ensureNotNull(prefix, "prefix");
        this.ttl = ensureNotNull(ttl, "ttl");
        this.jsonMapper = new DefaultGsonObjectMapper();
        this.redisOperations =
                RedisOperationsFactory.createRedisOperations(ensureNotNull(storeType, "storeType"), client, jsonMapper);
    }

    /**
     * Creates a new builder instance for constructing a RedisChatMemoryStore.
     *
     * @return A new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Retrieves all chat messages associated with the given memory ID.
     *
     * @param memoryId The identifier for the memory to retrieve
     * @return List of chat messages or an empty list if no messages found
     */
    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return redisOperations.getMessages(toRedisKey(memoryId));
    }

    /**
     * Updates the messages associated with the given memory ID.
     * If TTL is set, the keys will automatically expire after the specified duration.
     *
     * @param memoryId The identifier for the memory to update
     * @param messages The list of messages to store
     * @throws RedisChatMemoryStoreException If the Redis operation fails
     */
    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String json = ChatMessageSerializer.messagesToJson(ensureNotEmpty(messages, "messages"));
        redisOperations.updateMessages(toRedisKey(memoryId), json, ttl);
    }

    /**
     * Deletes all messages associated with the given memory ID.
     *
     * @param memoryId The identifier for the memory to delete
     */
    @Override
    public void deleteMessages(Object memoryId) {
        redisOperations.deleteMessages(toRedisKey(memoryId));
    }

    /**
     * Converts a memory ID object to a string representation.
     *
     * @param memoryId The memory ID to convert
     * @return String representation of the memory ID
     * @throws IllegalArgumentException If memoryId is null or empty
     */
    private String toMemoryIdString(Object memoryId) {
        boolean isNullOrEmpty = memoryId == null || memoryId.toString().trim().isEmpty();
        if (isNullOrEmpty) {
            throw new IllegalArgumentException("memoryId cannot be null or empty");
        }
        return memoryId.toString();
    }

    /**
     * Generates a Redis key for the given memory ID by applying the configured prefix.
     *
     * @param memoryId The memory ID to generate a key for
     * @return The Redis key string
     */
    private String toRedisKey(Object memoryId) {
        return keyPrefix + toMemoryIdString(memoryId);
    }

    /**
     * Builder for creating RedisChatMemoryStore instances with fluent API.
     */
    public static class Builder {

        private String host;
        private Integer port;
        private String user;
        private String password;
        private Long ttl = 0L;
        private String prefix = "";
        private StoreType storeType = StoreType.JSON;

        /**
         * Sets the Redis host.
         *
         * @param host The Redis server hostname or IP address
         * @return This builder for method chaining
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * Sets the Redis port.
         *
         * @param port The Redis server port
         * @return This builder for method chaining
         */
        public Builder port(Integer port) {
            this.port = port;
            return this;
        }

        /**
         * Sets the Redis user for authentication.
         *
         * @param user The Redis username
         * @return This builder for method chaining
         */
        public Builder user(String user) {
            this.user = user;
            return this;
        }

        /**
         * Sets the Redis password for authentication.
         *
         * @param password The Redis password
         * @return This builder for method chaining
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Sets the Time-To-Live (TTL) value for the Redis keys.
         * This value determines how long the keys will persist in Redis before being automatically deleted.
         *
         * @param ttl The TTL value in seconds. A value of 0 or less means the keys will not expire.
         * @return The Builder instance for method chaining.
         */
        public Builder ttl(Long ttl) {
            this.ttl = ttl;
            return this;
        }

        /**
         * Sets the prefix to be used for Redis keys.
         * This prefix is prepended to all keys stored in Redis, allowing for better organization or namespacing.
         * Usually would end with a colon. ex "chat:"
         *
         * @param prefix The prefix string to be added to Redis keys.
         * @return The Builder instance for method chaining.
         */
        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        /**
         * Sets the type of data you want to save.
         * This parameter is used to configure the data structure in which the message is saved to redis, and the RedisJson type is used by default.
         * It can also be set to the redis native String type.
         *
         * @param storeType The data structure used to save the data.
         * @return he Builder instance for method chaining.
         */
        public Builder storeType(StoreType storeType) {
            this.storeType = storeType;
            return this;
        }

        /**
         * Builds a new RedisChatMemoryStore instance with the configured parameters.
         *
         * @return A new RedisChatMemoryStore instance
         */
        public RedisChatMemoryStore build() {
            return new RedisChatMemoryStore(host, port, user, password, prefix, ttl, storeType);
        }
    }
}
