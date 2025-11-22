package dev.langchain4j.community.store.memory.chat.redis;

import dev.langchain4j.data.message.ChatMessage;
import java.util.List;

/**
 * Interface for handling metadata storage and retrieval in Redis ChatMemory Store.
 * <p>
 * Different implementations provide different strategies for storing metadata:
 * - String storage for native redis string
 * - JSON storage for RedisJson plugin
 * <p>
 * This design allows for flexible metadata handling while maintaining type safety
 * and optimal performance for different use cases.
 */
public interface RedisOperations {

    /**
     * Retrieves all chat messages associated with the given converted memory key.
     *
     * @param key The identifier for the memory to retrieve
     * @return List of chat messages or an empty list if no messages found
     */
    List<ChatMessage> getMessages(String key);

    /**
     * Updates the messages associated with the given converted memory key.
     * If TTL is set, the keys will automatically expire after the specified duration.
     *
     * @param key The identifier for the memory to update
     * @param message The list of messages to store
     * @param ttl Time-to-live value for Redis keys in seconds.
     */
    void updateMessages(String key, String message, Long ttl);

    /**
     * Deletes all messages associated with the given converted memory key.
     *
     * @param key The identifier for the memory to delete
     */
    void deleteMessages(String key);
}
