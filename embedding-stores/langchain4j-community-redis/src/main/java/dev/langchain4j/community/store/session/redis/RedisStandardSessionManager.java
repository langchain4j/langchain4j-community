package dev.langchain4j.community.store.session.redis;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.json.Path;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

/**
 * Redis-based standard session manager implementation for LangChain4j.
 *
 * <p>This class provides a Redis-based storage solution for conversation history without
 * semantic search capabilities. It uses Redis JSON to store chat messages and timestamps
 * for sequential retrieval.</p>
 */
public class RedisStandardSessionManager extends BaseRedisSessionManager {

    private static final Path ROOT_PATH = Path.ROOT_PATH;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Creates a new RedisStandardSessionManager with the specified parameters.
     *
     * @param redis The Redis client
     * @param name The name of the session manager (used as part of the Redis key)
     * @param prefix The prefix for Redis keys (optional, defaults to the name)
     * @param sessionTag The session tag (optional, a random UUID will be generated if not provided)
     */
    public RedisStandardSessionManager(JedisPooled redis, String name, String prefix, String sessionTag) {
        super(redis, name, prefix, sessionTag);
    }

    @Override
    public void addMessage(Map<String, String> message) {
        ensureNotNull(message, "message");
        addMessages(Collections.singletonList(message));
    }

    @Override
    public void addMessages(List<Map<String, String>> messages) {
        ensureNotNull(messages, "messages");

        if (messages.isEmpty()) {
            return;
        }

        for (Map<String, String> message : messages) {
            String role = message.get(ChatMessage.ROLE_FIELD_NAME);
            String content = message.get(ChatMessage.CONTENT_FIELD_NAME);
            String toolCallId = message.get(ChatMessage.TOOL_CALL_ID_FIELD_NAME);

            ChatMessage chatMessage = ChatMessage.builder()
                    .role(role)
                    .content(content)
                    .sessionTag(sessionTag)
                    .toolCallId(toolCallId)
                    .build();

            try {
                String entryId = chatMessage.getEntryId();
                String jsonString = OBJECT_MAPPER.writeValueAsString(chatMessage.toMap());
                redis.jsonSet(entryId, ROOT_PATH, jsonString);
            } catch (JsonProcessingException e) {
                throw new RedisSessionException("Failed to serialize message", e);
            }
        }
    }

    @Override
    public List<?> getRecent(int limit, boolean asText) {
        if (limit <= 0) {
            return Collections.emptyList();
        }

        // Find all keys for this session
        String pattern = this.prefix + ":" + this.sessionTag + ":*";
        ScanParams params = new ScanParams().match(pattern).count(100);
        String cursor = "0";
        List<String> keys = new ArrayList<>();

        do {
            ScanResult<String> scanResult = redis.scan(cursor, params);
            keys.addAll(scanResult.getResult());
            cursor = scanResult.getCursor();
        } while (!cursor.equals("0"));

        // Read all messages
        List<ChatMessage> messages = new ArrayList<>();
        for (String key : keys) {
            Object jsonObj = redis.jsonGet(key, ROOT_PATH);
            String json = jsonObj != null ? jsonObj.toString() : null;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> messageData = OBJECT_MAPPER.readValue(json, Map.class);
                ChatMessage message = ChatMessage.fromMap(messageData, sessionTag);
                messages.add(message);
            } catch (JsonProcessingException e) {
                throw new RedisSessionException("Failed to deserialize message", e);
            }
        }

        // Sort by timestamp in descending order (newest first)
        messages.sort(Comparator.comparing(ChatMessage::getTimestamp).reversed());

        // Get the first N messages and reverse to chronological order
        List<ChatMessage> limitedMessages = messages.subList(0, Math.min(limit, messages.size()));
        Collections.reverse(limitedMessages);

        // Format the messages as requested
        return formatMessages(limitedMessages, asText);
    }

    @Override
    public List<Map<String, String>> getMessages() {
        // Use getRecent with a high limit to get all messages
        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages = (List<Map<String, String>>) getRecent(Integer.MAX_VALUE, false);
        return messages;
    }

    @Override
    public void clear() {
        // Find all keys for this session
        String pattern = this.prefix + ":" + this.sessionTag + ":*";
        ScanParams params = new ScanParams().match(pattern);
        String cursor = "0";
        List<String> keys = new ArrayList<>();

        do {
            ScanResult<String> scanResult = redis.scan(cursor, params);
            keys.addAll(scanResult.getResult());
            cursor = scanResult.getCursor();
        } while (!cursor.equals("0"));

        // Delete all keys
        if (!keys.isEmpty()) {
            redis.del(keys.toArray(new String[0]));
        }
    }

    @Override
    public void delete(String messageId) {
        if (messageId == null) {
            // If no ID is provided, delete the most recent message
            List<Map<String, String>> messages = getMessages();
            if (!messages.isEmpty()) {
                String lastMessage = findLastMessageKey();
                if (lastMessage != null) {
                    redis.del(lastMessage);
                }
            }
        } else {
            // Delete the specified message
            redis.del(messageId);
        }
    }

    /**
     * Finds the key of the most recent message in the session.
     *
     * @return The key of the most recent message, or null if there are no messages
     */
    private String findLastMessageKey() {
        String pattern = this.prefix + ":" + this.sessionTag + ":*";
        ScanParams params = new ScanParams().match(pattern);
        String cursor = "0";
        List<String> keys = new ArrayList<>();

        do {
            ScanResult<String> scanResult = redis.scan(cursor, params);
            keys.addAll(scanResult.getResult());
            cursor = scanResult.getCursor();
        } while (!cursor.equals("0"));

        if (keys.isEmpty()) {
            return null;
        }

        // Extract timestamp from keys and find the max
        long maxTimestamp = 0;
        String newestKey = null;

        for (String key : keys) {
            String[] parts = key.split(":");
            if (parts.length >= 3) {
                try {
                    long timestamp = Long.parseLong(parts[parts.length - 1]);
                    if (timestamp > maxTimestamp) {
                        maxTimestamp = timestamp;
                        newestKey = key;
                    }
                } catch (NumberFormatException e) {
                    // Ignore keys with invalid timestamps
                }
            }
        }

        return newestKey;
    }

    /**
     * Builder for creating RedisStandardSessionManager instances.
     */
    public static class Builder {
        private JedisPooled redis;
        private String name;
        private String prefix;
        private String sessionTag;

        /**
         * Creates a new Builder instance with default values.
         * Use this builder to configure and create a RedisStandardSessionManager.
         */
        public Builder() {
            // Default constructor
        }

        /**
         * Sets the Redis client.
         *
         * @param redis The Redis client
         * @return This builder
         */
        public Builder redis(JedisPooled redis) {
            this.redis = redis;
            return this;
        }

        /**
         * Sets the name of the session manager.
         *
         * @param name The name
         * @return This builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the prefix for Redis keys.
         *
         * @param prefix The prefix
         * @return This builder
         */
        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        /**
         * Sets the session tag.
         *
         * @param sessionTag The session tag
         * @return This builder
         */
        public Builder sessionTag(String sessionTag) {
            this.sessionTag = sessionTag;
            return this;
        }

        /**
         * Builds a new RedisStandardSessionManager with the configured parameters.
         *
         * @return A new RedisStandardSessionManager instance
         */
        public RedisStandardSessionManager build() {
            ensureNotNull(redis, "Redis client is required");
            ensureNotNull(name, "Name is required");

            return new RedisStandardSessionManager(redis, name, prefix, sessionTag);
        }
    }

    /**
     * Creates a new builder for the RedisStandardSessionManager.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
}
