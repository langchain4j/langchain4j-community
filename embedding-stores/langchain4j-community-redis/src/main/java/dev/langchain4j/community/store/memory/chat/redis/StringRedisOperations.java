package dev.langchain4j.community.store.memory.chat.redis;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import java.util.ArrayList;
import java.util.List;
import redis.clients.jedis.UnifiedJedis;

/**
 * String storage mode in Redis
 * Use String commands to store and retrieve data
 */
public class StringRedisOperations implements RedisOperations {

    private final UnifiedJedis client;

    StringRedisOperations(UnifiedJedis client) {
        this.client = client;
    }

    @Override
    public List<ChatMessage> getMessages(String key) {
        String json = client.get(key);
        return json == null ? new ArrayList<>() : ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(String key, String message, Long ttl) {
        String res;
        if (ttl > 0) {
            res = client.setex(key, ttl, message);
        } else {
            res = client.set(key, message);
        }
        if (!"OK".equals(res)) {
            throw new RedisChatMemoryStoreException("Set memory error, msg=" + res);
        }
    }

    @Override
    public void deleteMessages(String key) {
        client.del(key);
    }
}
