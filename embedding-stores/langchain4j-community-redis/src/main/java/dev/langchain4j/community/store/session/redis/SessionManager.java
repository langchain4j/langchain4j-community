package dev.langchain4j.community.store.session.redis;

import java.util.List;
import java.util.Map;

/**
 * Interface defining the core functionality for a session manager.
 *
 * <p>A session manager helps applications maintain conversational state by storing
 * and retrieving message histories.</p>
 */
public interface SessionManager extends AutoCloseable {

    /**
     * Stores a user prompt and an LLM response in the session.
     *
     * @param prompt The user prompt to store
     * @param response The LLM response to store
     */
    void store(String prompt, String response);

    /**
     * Adds a single message to the session history.
     *
     * @param message The message to add, with "role" and "content" fields
     */
    void addMessage(Map<String, String> message);

    /**
     * Adds multiple messages to the session history.
     *
     * @param messages The list of messages to add, each with "role" and "content" fields
     */
    void addMessages(List<Map<String, String>> messages);

    /**
     * Retrieves the most recent messages from the session history.
     *
     * @param limit The maximum number of messages to retrieve
     * @param asText Whether to return the messages as plain text instead of structured objects
     * @return The recent messages as either text strings or maps
     */
    List<?> getRecent(int limit, boolean asText);

    /**
     * Retrieves the full session history.
     *
     * @return All session messages as maps with role and content
     */
    List<Map<String, String>> getMessages();

    /**
     * Clears the session history.
     */
    void clear();

    /**
     * Deletes a specific message from the session history.
     *
     * @param messageId The ID of the message to delete, or null to delete the most recent message
     */
    void delete(String messageId);
}
