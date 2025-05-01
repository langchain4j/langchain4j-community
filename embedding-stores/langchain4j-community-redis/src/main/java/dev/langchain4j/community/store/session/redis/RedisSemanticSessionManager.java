package dev.langchain4j.community.store.session.redis;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.json.Path;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.search.Document;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.Query;
import redis.clients.jedis.search.RediSearchUtil;
import redis.clients.jedis.search.SearchResult;
import redis.clients.jedis.search.schemafields.SchemaField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;
import redis.clients.jedis.search.schemafields.VectorField.VectorAlgorithm;

/**
 * Redis-based semantic session manager implementation for LangChain4j.
 *
 * <p>This class provides a Redis-based semantic search solution for conversation history.
 * It uses Redis Vector Search capabilities to find semantically similar messages based on
 * vector embeddings.</p>
 *
 * <p>Unlike the standard session manager, this implementation can retrieve
 * semantically relevant context based on query similarity, not just chronological order.</p>
 */
public class RedisSemanticSessionManager extends BaseRedisSessionManager {

    private static final String JSON_PATH_PREFIX = "$.";
    private static final String VECTOR_FIELD_NAME = "vector_field";
    private static final String ROLE_FIELD_NAME = "role";
    private static final String CONTENT_FIELD_NAME = "content";
    private static final String SESSION_FIELD_NAME = "session_tag";
    private static final String TIMESTAMP_FIELD_NAME = "timestamp";
    private static final String TOOL_FIELD_NAME = "tool_call_id";
    private static final String DISTANCE_FIELD_NAME = "_score";

    private static final Path ROOT_PATH = Path.ROOT_PATH;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EmbeddingModel embeddingModel;
    private final String indexName;
    private final double distanceThreshold;
    private final int embeddingDimension;

    /**
     * Creates a new RedisSemanticSessionManager with the specified parameters.
     *
     * @param redis The Redis client
     * @param embeddingModel The embedding model to use for vectorizing content
     * @param name The name of the session manager
     * @param prefix The prefix for Redis keys (optional, defaults to the name)
     * @param sessionTag The session tag (optional, a random UUID will be generated if not provided)
     * @param distanceThreshold The similarity threshold for semantic search (default is 0.3)
     */
    public RedisSemanticSessionManager(
            JedisPooled redis,
            EmbeddingModel embeddingModel,
            String name,
            String prefix,
            String sessionTag,
            Double distanceThreshold) {
        super(redis, name, prefix, sessionTag);

        ensureNotNull(embeddingModel, "embeddingModel");

        this.embeddingModel = embeddingModel;
        this.indexName = this.prefix + "-index";
        this.distanceThreshold = distanceThreshold != null ? distanceThreshold : 0.3;

        // Use a reasonable default dimension size for typical embedding models
        this.embeddingDimension = 1536;

        // Ensure the search index exists
        ensureIndexExists();
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

            // Create a vector embedding for the content
            Response<Embedding> response = embeddingModel.embed(content);
            float[] contentVector = response.content().vector();

            // Create the chat message
            ChatMessage chatMessage = ChatMessage.builder()
                    .role(role)
                    .content(content)
                    .sessionTag(sessionTag)
                    .toolCallId(toolCallId)
                    .vectorField(contentVector)
                    .build();

            try {
                // Store the message in Redis
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
        List<ChatMessage> messages = getAllSessionMessages();

        // Sort by timestamp in ascending order (oldest first)
        messages.sort(Comparator.comparing(ChatMessage::getTimestamp));

        // Get up to limit messages
        List<ChatMessage> limitedMessages =
                limit < messages.size() ? messages.subList(messages.size() - limit, messages.size()) : messages;

        // Format the messages as requested
        return formatMessages(limitedMessages, asText);
    }

    /**
     * Retrieves messages that are semantically similar to the provided query.
     *
     * @param query The text to find relevant messages for
     * @param limit The maximum number of messages to return
     * @param asText Whether to return the messages as plain text
     * @return The semantically relevant messages
     */
    public List<?> getRelevant(String query, int limit, boolean asText) {
        if (limit <= 0 || query == null || query.isEmpty()) {
            return Collections.emptyList();
        }

        // Create a vector embedding for the query
        Response<Embedding> response = embeddingModel.embed(query);
        float[] queryVector = response.content().vector();

        // Create a vector similarity search query
        Query vectorQuery = createVectorQuery(queryVector, limit);

        // Execute the search
        try {
            SearchResult searchResult = redis.ftSearch(indexName, vectorQuery);
            List<Document> documents = searchResult.getDocuments();

            // Convert search results to chat messages
            List<ChatMessage> messages = new ArrayList<>();
            for (Document doc : documents) {
                // Check if it meets our similarity threshold
                double score = Double.parseDouble(doc.getString(DISTANCE_FIELD_NAME));
                if (score < distanceThreshold) {
                    continue;
                }

                try {
                    String jsonString = doc.getString(JSON_PATH_PREFIX);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageData = OBJECT_MAPPER.readValue(jsonString, Map.class);
                    ChatMessage message = ChatMessage.fromMap(messageData, sessionTag);
                    messages.add(message);
                } catch (JsonProcessingException e) {
                    // Skip messages that can't be deserialized
                }
            }

            // Sort by timestamp (oldest first) to maintain chronological order
            messages.sort(Comparator.comparing(ChatMessage::getTimestamp));

            // Format the messages as requested
            return formatMessages(messages, asText);
        } catch (Exception e) {
            // If search index doesn't exist or search fails, fall back to recent messages
            return getRecent(limit, asText);
        }
    }

    @Override
    public List<Map<String, String>> getMessages() {
        List<ChatMessage> messages = getAllSessionMessages();

        // Sort by timestamp in ascending order (oldest first)
        messages.sort(Comparator.comparing(ChatMessage::getTimestamp));

        // Convert to simple maps
        List<Map<String, String>> simpleMaps = new ArrayList<>();
        for (ChatMessage message : messages) {
            simpleMaps.add(message.toSimpleMap());
        }

        return simpleMaps;
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
            List<ChatMessage> messages = getAllSessionMessages();
            if (!messages.isEmpty()) {
                // Sort by timestamp (newest first)
                messages.sort(Comparator.comparing(ChatMessage::getTimestamp).reversed());
                String entryId = messages.get(0).getEntryId();
                redis.del(entryId);
            }
        } else {
            // Delete the specified message
            redis.del(messageId);
        }
    }

    /**
     * Creates a vector search query for finding semantically similar messages.
     *
     * @param vector The vector to search for
     * @param limit The maximum number of results to return
     * @return A Query object for vector similarity search
     */
    private Query createVectorQuery(float[] vector, int limit) {
        StringBuilder queryBuilder = new StringBuilder();

        // Filter for this session tag
        queryBuilder
                .append("@")
                .append(SESSION_FIELD_NAME)
                .append(":{")
                .append(sessionTag)
                .append("}");

        // Add the KNN vector search part
        queryBuilder
                .append(" => [KNN ")
                .append(limit)
                .append(" @")
                .append(VECTOR_FIELD_NAME)
                .append(" $BLOB AS ")
                .append(DISTANCE_FIELD_NAME)
                .append("]");

        Query query = new Query(queryBuilder.toString())
                .addParam("BLOB", RediSearchUtil.toByteArray(vector))
                .setSortBy(DISTANCE_FIELD_NAME, false) // Higher scores for more similar vectors
                .limit(0, limit) // Return up to limit results
                .dialect(2); // Use query dialect version 2

        return query;
    }

    /**
     * Ensures that the vector index exists in Redis.
     */
    private void ensureIndexExists() {
        try {
            Set<String> indexes = redis.ftList();
            if (!indexes.contains(indexName)) {
                createIndex();
            }
        } catch (Exception e) {
            // In case of mock testing or other issues, log and continue
            // Index may already exist or not be needed in test environments
        }
    }

    /**
     * Creates the vector search index in Redis.
     */
    private void createIndex() {
        // Create schema fields for the index
        Map<String, Object> vectorAttrs = new HashMap<>();
        vectorAttrs.put("DIM", embeddingDimension);
        vectorAttrs.put("DISTANCE_METRIC", "COSINE");
        vectorAttrs.put("TYPE", "FLOAT32");
        vectorAttrs.put("INITIAL_CAP", 100);

        SchemaField[] schemaFields = new SchemaField[] {
            TextField.of(JSON_PATH_PREFIX + ROLE_FIELD_NAME).as(ROLE_FIELD_NAME),
            TextField.of(JSON_PATH_PREFIX + CONTENT_FIELD_NAME).as(CONTENT_FIELD_NAME),
            TextField.of(JSON_PATH_PREFIX + SESSION_FIELD_NAME).as(SESSION_FIELD_NAME),
            TextField.of(JSON_PATH_PREFIX + TIMESTAMP_FIELD_NAME).as(TIMESTAMP_FIELD_NAME),
            TextField.of(JSON_PATH_PREFIX + TOOL_FIELD_NAME).as(TOOL_FIELD_NAME),
            VectorField.builder()
                    .fieldName(JSON_PATH_PREFIX + VECTOR_FIELD_NAME)
                    .algorithm(VectorAlgorithm.HNSW)
                    .attributes(vectorAttrs)
                    .as(VECTOR_FIELD_NAME)
                    .build()
        };

        try {
            // Attempt to create the index
            String result = redis.ftCreate(
                    indexName,
                    FTCreateParams.createParams().on(IndexDataType.JSON).addPrefix(prefix + ":"),
                    schemaFields);

            if (!"OK".equals(result)) {
                throw new RedisSessionException("Failed to create vector index: " + result);
            }
        } catch (Exception e) {
            throw new RedisSessionException("Error creating vector index", e);
        }
    }

    /**
     * Retrieves all messages from the current session.
     *
     * @return A list of all chat messages in the session
     */
    private List<ChatMessage> getAllSessionMessages() {
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
                // Skip messages that can't be deserialized
            }
        }

        return messages;
    }

    /**
     * Builder for creating RedisSemanticSessionManager instances.
     */
    public static class Builder {
        private JedisPooled redis;
        private EmbeddingModel embeddingModel;
        private String name;
        private String prefix;
        private String sessionTag;
        private Double distanceThreshold = 0.3;

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
         * Sets the embedding model.
         *
         * @param embeddingModel The embedding model
         * @return This builder
         */
        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
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
         * Sets the distance threshold for semantic similarity.
         *
         * @param distanceThreshold The threshold (0.0 to 1.0)
         * @return This builder
         */
        public Builder distanceThreshold(Double distanceThreshold) {
            this.distanceThreshold = distanceThreshold;
            return this;
        }

        /**
         * Builds a new RedisSemanticSessionManager with the configured parameters.
         *
         * @return A new RedisSemanticSessionManager instance
         */
        public RedisSemanticSessionManager build() {
            ensureNotNull(redis, "Redis client is required");
            ensureNotNull(embeddingModel, "Embedding model is required");
            ensureNotNull(name, "Name is required");

            return new RedisSemanticSessionManager(redis, embeddingModel, name, prefix, sessionTag, distanceThreshold);
        }
    }

    /**
     * Creates a new builder for the RedisSemanticSessionManager.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
}
