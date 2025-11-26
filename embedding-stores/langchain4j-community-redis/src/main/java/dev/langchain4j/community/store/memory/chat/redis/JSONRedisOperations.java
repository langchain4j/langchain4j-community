package dev.langchain4j.community.store.memory.chat.redis;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import java.util.ArrayList;
import java.util.List;
import redis.clients.jedis.AbstractPipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.json.JsonObjectMapper;

/**
 * RedisJson storage mode in Redis
 * Use Json commands to store and retrieve data
 */
public class JSONRedisOperations implements RedisOperations {

    private final UnifiedJedis client;
    private final JsonObjectMapper jsonMapper;

    JSONRedisOperations(UnifiedJedis client, JsonObjectMapper jsonMapper) {
        this.client = client;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public List<ChatMessage> getMessages(String key) {
        String json = jsonMapper.toJson(client.jsonGet(key));
        return json == null ? new ArrayList<>() : ChatMessageDeserializer.messagesFromJson(json);
    }

    @Override
    public void updateMessages(String key, String message, Long ttl) {
        String res;
        if (ttl > 0) {
            try (AbstractPipeline pipeline = client.pipelined()) {
                Response<String> jsonSetResponse = pipeline.jsonSet(key, message);
                Response<Long> expireResponse = pipeline.expire(key, ttl);
                pipeline.sync();
                res = jsonSetResponse.get();
            }
        } else {
            res = client.jsonSet(key, message);
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
