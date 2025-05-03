package dev.langchain4j.community.store.session.redis;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import redis.clients.jedis.JedisPooled;

/**
 * Base class for Redis session managers.
 *
 * <p>This abstract class provides common functionality for Redis-based session managers,
 * including session identification and message formatting.</p>
 */
public abstract class BaseRedisSessionManager implements SessionManager {

    /** The Redis client used to store and retrieve session data. */
    protected final JedisPooled redis;

    /** The unique identifier for this session. */
    protected final String sessionTag;

    /** The name of the session manager, used as the index name. */
    protected final String name;

    /** Prefix added to all Redis keys for this session manager. */
    protected final String prefix;

    /**
     * Creates a new BaseRedisSessionManager with the specified parameters.
     *
     * @param redis The Redis client
     * @param name The name of the session manager index
     * @param prefix The prefix for Redis keys (optional, defaults to the name)
     * @param sessionTag The session tag (optional, a random UUID will be generated if not provided)
     */
    protected BaseRedisSessionManager(JedisPooled redis, String name, String prefix, String sessionTag) {
        ensureNotNull(redis, "redis");
        ensureNotBlank(name, "name");

        this.redis = redis;
        this.name = name;
        this.prefix = prefix != null ? prefix : name;
        this.sessionTag = sessionTag != null ? sessionTag : UUID.randomUUID().toString();
    }

    @Override
    public void store(String prompt, String response) {
        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put(ChatMessage.ROLE_FIELD_NAME, "user");
        userMessage.put(ChatMessage.CONTENT_FIELD_NAME, prompt);
        messages.add(userMessage);

        Map<String, String> llmMessage = new HashMap<>();
        llmMessage.put(ChatMessage.ROLE_FIELD_NAME, "llm");
        llmMessage.put(ChatMessage.CONTENT_FIELD_NAME, response);
        messages.add(llmMessage);

        addMessages(messages);
    }

    @Override
    public void close() {
        // Note: We don't close the redis client here because it may be shared
        // with other components. The client should be closed by the code that created it.
    }

    /**
     * Formats a list of chat messages as either simple objects or plain text.
     *
     * @param messages The messages to format
     * @param asText Whether to return the messages as plain text
     * @return The formatted messages
     */
    protected List<?> formatMessages(List<ChatMessage> messages, boolean asText) {
        if (asText) {
            List<String> textMessages = new ArrayList<>();
            for (ChatMessage message : messages) {
                textMessages.add(message.getContent());
            }
            return textMessages;
        } else {
            List<Map<String, String>> mapMessages = new ArrayList<>();
            for (ChatMessage message : messages) {
                mapMessages.add(message.toSimpleMap());
            }
            return mapMessages;
        }
    }

    /**
     * Generates a unique key for a session entry.
     *
     * @param sessionTag The session tag
     * @param timestamp The timestamp
     * @return A key in the format "prefix:sessionTag:timestamp"
     */
    protected String generateKey(String sessionTag, long timestamp) {
        return String.format("%s:%s:%d", prefix, sessionTag, timestamp);
    }
}
