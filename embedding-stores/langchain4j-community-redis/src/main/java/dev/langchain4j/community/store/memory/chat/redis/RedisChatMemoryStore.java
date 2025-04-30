package dev.langchain4j.community.store.memory.chat.redis;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.util.ArrayList;
import java.util.List;
import redis.clients.jedis.JedisPooled;

public class RedisChatMemoryStore implements ChatMemoryStore {

    private final JedisPooled client;
    private final String keyPrefix;
    private final Long ttl;

    public RedisChatMemoryStore(String host, Integer port, String user, String password) {
        this(host, port, user, password, "", 0L);
    }

    public RedisChatMemoryStore(String host, Integer port, String user, String password, String prefix, Long ttl) {
        String finalHost = ensureNotBlank(host, "host");
        int finalPort = ensureNotNull(port, "port");
        if (user != null) {
            String finalUser = ensureNotBlank(user, "user");
            String finalPassword = ensureNotBlank(password, "password");
            this.client = new JedisPooled(finalHost, finalPort, finalUser, finalPassword);
        } else {
            this.client = new JedisPooled(finalHost, finalPort);
        }
        this.keyPrefix = ensureNotNull(prefix, "prefix");
        this.ttl = ensureNotNull(ttl, "ttl");
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = client.get(toRedisKey(memoryId));
        return json == null ? new ArrayList<>() : ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String json = ChatMessageSerializer.messagesToJson(ensureNotEmpty(messages, "messages"));
        String key = toRedisKey(memoryId);
        String res;
        if (ttl > 0) {
            res = client.setex(key, ttl, json);
        } else {
            res = client.set(key, json);
        }
        if (!"OK".equals(res)) {
            throw new RedisChatMemoryStoreException("Set memory error, msg=" + res);
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        client.del(toRedisKey(memoryId));
    }

    private String toMemoryIdString(Object memoryId) {
        boolean isNullOrEmpty = memoryId == null || memoryId.toString().trim().isEmpty();
        if (isNullOrEmpty) {
            throw new IllegalArgumentException("memoryId cannot be null or empty");
        }
        return memoryId.toString();
    }

    /**
     * Get the Redis key for a memory ID
     */
    private String toRedisKey(Object memoryId) {
        return keyPrefix + toMemoryIdString(memoryId);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String host;
        private Integer port;
        private String user;
        private String password;
        private Long ttl = 0L;
        private String prefix = "";

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(Integer port) {
            this.port = port;
            return this;
        }

        public Builder user(String user) {
            this.user = user;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder ttl(Long ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public RedisChatMemoryStore build() {
            return new RedisChatMemoryStore(host, port, user, password, prefix, ttl);
        }
    }
}
