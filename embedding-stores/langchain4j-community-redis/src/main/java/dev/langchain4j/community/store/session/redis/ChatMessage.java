package dev.langchain4j.community.store.session.redis;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a single chat message exchanged between a user and an LLM.
 */
public class ChatMessage {

    public static final String ID_FIELD_NAME = "entry_id";
    public static final String ROLE_FIELD_NAME = "role";
    public static final String CONTENT_FIELD_NAME = "content";
    public static final String SESSION_FIELD_NAME = "session_tag";
    public static final String TIMESTAMP_FIELD_NAME = "timestamp";
    public static final String TOOL_CALL_ID_FIELD_NAME = "tool_call_id";
    public static final String VECTOR_FIELD_NAME = "vector_field";

    private final String entryId;
    private final String role;
    private final String content;
    private final String sessionTag;
    private final long timestamp;
    private final String toolCallId;
    private final float[] vectorField;

    private ChatMessage(Builder builder) {
        this.entryId = builder.entryId;
        this.role = builder.role;
        this.content = builder.content;
        this.sessionTag = builder.sessionTag;
        this.timestamp = builder.timestamp;
        this.toolCallId = builder.toolCallId;
        this.vectorField = builder.vectorField;
    }

    /**
     * Converts the chat message to a map representation.
     *
     * @return A map containing the message fields
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put(ID_FIELD_NAME, entryId);
        map.put(ROLE_FIELD_NAME, role);
        map.put(CONTENT_FIELD_NAME, content);
        map.put(SESSION_FIELD_NAME, sessionTag);
        map.put(TIMESTAMP_FIELD_NAME, timestamp);

        if (toolCallId != null) {
            map.put(TOOL_CALL_ID_FIELD_NAME, toolCallId);
        }

        if (vectorField != null) {
            map.put(VECTOR_FIELD_NAME, vectorField);
        }

        return map;
    }

    /**
     * Returns a simplified representation of the message.
     *
     * @return A map containing only the role and content fields
     */
    public Map<String, String> toSimpleMap() {
        Map<String, String> map = new HashMap<>();
        map.put(ROLE_FIELD_NAME, role);
        map.put(CONTENT_FIELD_NAME, content);

        if (toolCallId != null) {
            map.put(TOOL_CALL_ID_FIELD_NAME, toolCallId);
        }

        return map;
    }

    public String getEntryId() {
        return entryId;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getSessionTag() {
        return sessionTag;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getToolCallId() {
        return toolCallId;
    }

    public float[] getVectorField() {
        return vectorField;
    }

    /**
     * Creates a new builder for the ChatMessage.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a builder initialized with the values from an existing message.
     *
     * @param source The message to copy values from
     * @return A new builder instance
     */
    public static Builder builder(ChatMessage source) {
        return new Builder()
                .entryId(source.entryId)
                .role(source.role)
                .content(source.content)
                .sessionTag(source.sessionTag)
                .timestamp(source.timestamp)
                .toolCallId(source.toolCallId)
                .vectorField(source.vectorField);
    }

    /**
     * Builder for constructing ChatMessage instances.
     */
    public static class Builder {
        private String entryId;
        private String role;
        private String content;
        private String sessionTag;
        private long timestamp = System.currentTimeMillis();
        private String toolCallId;
        private float[] vectorField;

        public Builder entryId(String entryId) {
            this.entryId = entryId;
            return this;
        }

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder sessionTag(String sessionTag) {
            this.sessionTag = sessionTag;
            return this;
        }

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder toolCallId(String toolCallId) {
            this.toolCallId = toolCallId;
            return this;
        }

        public Builder vectorField(float[] vectorField) {
            this.vectorField = vectorField;
            return this;
        }

        public ChatMessage build() {
            if (role == null || role.isEmpty()) {
                throw new IllegalArgumentException("Role cannot be null or empty");
            }
            if (content == null || content.isEmpty()) {
                throw new IllegalArgumentException("Content cannot be null or empty");
            }
            if (sessionTag == null || sessionTag.isEmpty()) {
                throw new IllegalArgumentException("Session tag cannot be null or empty");
            }
            if (entryId == null) {
                entryId = sessionTag + ":" + timestamp;
            }

            return new ChatMessage(this);
        }
    }

    /**
     * Factory method to create a user message.
     *
     * @param content The message content
     * @param sessionTag The session tag
     * @return A new ChatMessage with role "user"
     */
    public static ChatMessage userMessage(String content, String sessionTag) {
        return builder().role("user").content(content).sessionTag(sessionTag).build();
    }

    /**
     * Factory method to create an LLM message.
     *
     * @param content The message content
     * @param sessionTag The session tag
     * @return A new ChatMessage with role "llm"
     */
    public static ChatMessage llmMessage(String content, String sessionTag) {
        return builder().role("llm").content(content).sessionTag(sessionTag).build();
    }

    /**
     * Factory method to create a message from a map.
     *
     * @param map The map containing message data
     * @param sessionTag The session tag to use if not present in the map
     * @return A new ChatMessage built from the map data
     */
    public static ChatMessage fromMap(Map<String, Object> map, String sessionTag) {
        Builder builder = builder();

        if (map.containsKey(ID_FIELD_NAME)) {
            builder.entryId((String) map.get(ID_FIELD_NAME));
        }

        builder.role((String) map.get(ROLE_FIELD_NAME));
        builder.content((String) map.get(CONTENT_FIELD_NAME));

        if (map.containsKey(SESSION_FIELD_NAME)) {
            builder.sessionTag((String) map.get(SESSION_FIELD_NAME));
        } else {
            builder.sessionTag(sessionTag);
        }

        if (map.containsKey(TIMESTAMP_FIELD_NAME)) {
            Object timestamp = map.get(TIMESTAMP_FIELD_NAME);
            if (timestamp instanceof Number) {
                builder.timestamp(((Number) timestamp).longValue());
            } else if (timestamp instanceof String) {
                builder.timestamp(Long.parseLong((String) timestamp));
            }
        }

        if (map.containsKey(TOOL_CALL_ID_FIELD_NAME)) {
            builder.toolCallId((String) map.get(TOOL_CALL_ID_FIELD_NAME));
        }

        if (map.containsKey(VECTOR_FIELD_NAME)) {
            Object vectorObj = map.get(VECTOR_FIELD_NAME);
            if (vectorObj instanceof float[]) {
                builder.vectorField((float[]) vectorObj);
            } else if (vectorObj instanceof double[]) {
                double[] doubleArray = (double[]) vectorObj;
                float[] floatArray = new float[doubleArray.length];
                for (int i = 0; i < doubleArray.length; i++) {
                    floatArray[i] = (float) doubleArray[i];
                }
                builder.vectorField(floatArray);
            }
        }

        return builder.build();
    }
}
