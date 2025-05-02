package dev.langchain4j.community.store.cache.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.community.store.filter.FilterExpression;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
 * Redis-based semantic cache implementation for LangChain4j.
 *
 * <p>This class provides a Redis-based semantic caching mechanism for language model responses,
 * allowing storage and retrieval of language model responses based on semantic similarity of prompts.</p>
 *
 * <p>The cache uses Redis Vector Search capabilities to find semantically similar prompts.
 * It uses cosine similarity to compare the vector embeddings of prompts.</p>
 *
 * <p>This implementation parallels the Python redis-semantic-cache in langchain-redis.</p>
 */
public class RedisSemanticCache implements AutoCloseable {

    private static final String LIB_NAME = "langchain4j-community-redis";
    private static final Path ROOT_PATH = Path.ROOT_PATH;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String JSON_PATH_PREFIX = "$.";
    private static final String VECTOR_FIELD_NAME = "prompt_vector";
    private static final String PROMPT_FIELD_NAME = "prompt";
    private static final String LLM_FIELD_NAME = "llm";
    private static final String RESPONSE_FIELD_NAME = "response";
    private static final String DISTANCE_FIELD_NAME = "_score";

    private final JedisPooled redis;
    private final EmbeddingModel embeddingModel;
    private final Integer ttl;
    private final String prefix;
    private final float similarityThreshold;
    private final String indexName;
    private final boolean enableEnhancedFilters;

    /**
     * Creates a new RedisSemanticCache with the specified parameters.
     *
     * @param redis The Redis client
     * @param embeddingModel The embedding model to use for vectorizing prompts
     * @param ttl Time-to-live for cache entries in seconds (null means no expiration)
     * @param prefix Prefix for all keys stored in Redis (default is "semantic-cache")
     * @param similarityThreshold The threshold for semantic similarity (default is 0.2)
     */
    public RedisSemanticCache(
            JedisPooled redis, EmbeddingModel embeddingModel, Integer ttl, String prefix, Float similarityThreshold) {
        this(redis, embeddingModel, ttl, prefix, similarityThreshold, false);
    }

    /**
     * Creates a new RedisSemanticCache with the specified parameters, including enhanced filter support.
     *
     * @param redis The Redis client
     * @param embeddingModel The embedding model to use for vectorizing prompts
     * @param ttl Time-to-live for cache entries in seconds (null means no expiration)
     * @param prefix Prefix for all keys stored in Redis (default is "semantic-cache")
     * @param similarityThreshold The threshold for semantic similarity (default is 0.2)
     * @param enableEnhancedFilters Whether to enable the enhanced Redis filter capabilities
     */
    public RedisSemanticCache(
            JedisPooled redis,
            EmbeddingModel embeddingModel,
            Integer ttl,
            String prefix,
            Float similarityThreshold,
            boolean enableEnhancedFilters) {
        this.redis = redis;
        this.embeddingModel = embeddingModel;
        this.ttl = ttl;
        this.prefix = prefix != null ? prefix : "semantic-cache";
        this.similarityThreshold = similarityThreshold != null ? similarityThreshold : 0.2f;
        this.indexName = this.prefix + "-index";
        this.enableEnhancedFilters = enableEnhancedFilters;

        // Check if the index exists, and create it if it doesn't
        ensureIndexExists();
    }

    /**
     * Looks up a cached response based on the prompt and LLM string using semantic similarity.
     *
     * @param prompt The prompt used for the LLM request
     * @param llmString A string representing the LLM and its configuration
     * @return The cached response for a semantically similar prompt, or null if not found
     */
    // No test-specific lookup methods - integration tests should use real Redis

    /**
     * Looks up a cached response based on the prompt and LLM string using semantic similarity.
     *
     * @param prompt The prompt used for the LLM request
     * @param llmString A string representing the LLM and its configuration
     * @return The cached response for a semantically similar prompt, or null if not found
     */
    public Response<?> lookup(String prompt, String llmString) {
        return lookup(prompt, llmString, null);
    }

    /**
     * Looks up a cached response based on the prompt and LLM string using semantic similarity.
     *
     * @param prompt The prompt used for the LLM request
     * @param llmString A string representing the LLM and its configuration
     * @param filter An optional filter to apply when searching for matches
     * @return The cached response for a semantically similar prompt, or null if not found
     */
    public Response<?> lookup(String prompt, String llmString, FilterExpression filter) {
        System.out.println("[DEBUG] RedisSemanticCache.lookup - Prompt: " + prompt);
        System.out.println("[DEBUG] RedisSemanticCache.lookup - LLM String: " + llmString);
        System.out.println("[DEBUG] RedisSemanticCache.lookup - Filter: "
                + (filter != null ? filter.toRedisQueryString() : "null"));

        // Convert the prompt to a vector embedding
        System.out.println("[DEBUG] RedisSemanticCache.lookup - Getting embedding for prompt");
        Response<Embedding> embeddingResponse = embeddingModel.embed(prompt);
        Embedding promptEmbedding = embeddingResponse.content();
        System.out.println(
                "[DEBUG] RedisSemanticCache.lookup - Got embedding, dimensions: " + promptEmbedding.vector().length);

        // Create a vector similarity search query
        System.out.println("[DEBUG] RedisSemanticCache.lookup - Creating vector query");
        Query query = createVectorQuery(promptEmbedding.vector(), llmString, filter);
        System.out.println("[DEBUG] RedisSemanticCache.lookup - Using index: " + indexName);

        try {
            SearchResult searchResult = null;

            try {
                // Execute the search (may throw if index doesn't exist yet)
                System.out.println("[DEBUG] RedisSemanticCache.lookup - Executing FT.SEARCH");
                searchResult = redis.ftSearch(indexName, query);
                System.out.println("[DEBUG] RedisSemanticCache.lookup - Search completed");
            } catch (Exception e) {
                System.out.println("[DEBUG] RedisSemanticCache.lookup - Search failed: " + e.getMessage());
                e.printStackTrace();
                throw new RedisCacheException("Failed to execute vector search query", e);
            }

            System.out.println("[DEBUG] RedisSemanticCache.lookup - Search result: "
                    + (searchResult == null ? "null" : "not null"));

            // Handle null result
            if (searchResult == null) {
                System.out.println("[DEBUG] RedisSemanticCache.lookup - Search result is null");
                return null; // No match in Redis
            }

            System.out.println("[DEBUG] RedisSemanticCache.lookup - Documents: "
                    + (searchResult.getDocuments() == null
                            ? "null"
                            : searchResult.getDocuments().size()));

            if (searchResult.getDocuments() == null
                    || searchResult.getDocuments().isEmpty()) {
                System.out.println("[DEBUG] RedisSemanticCache.lookup - No documents found");
                return null; // No matching documents in Redis
            }

            List<Document> documents = searchResult.getDocuments();
            System.out.println("[DEBUG] RedisSemanticCache.lookup - Found " + documents.size() + " documents");

            // Find a matching document with the right LLM
            Document bestMatch = null;

            // First try to find a document with matching LLM string
            for (Document doc : documents) {
                try {
                    String docLlmString = doc.getString(JSON_PATH_PREFIX + LLM_FIELD_NAME);
                    System.out.println("[DEBUG] RedisSemanticCache.lookup - Document LLM: " + docLlmString);

                    // If LLM strings match, this is a candidate
                    if (llmString.equals(docLlmString)) {
                        // Check if it meets our similarity threshold
                        double score = 1.0; // Default high score (less similar)

                        try {
                            score = Double.parseDouble(doc.getString(DISTANCE_FIELD_NAME));
                            System.out.println("[DEBUG] RedisSemanticCache.lookup - Score: " + score + ", Threshold: "
                                    + similarityThreshold);

                            if (score <= similarityThreshold) {
                                bestMatch = doc;
                                System.out.println(
                                        "[DEBUG] RedisSemanticCache.lookup - Found matching document with ID: "
                                                + doc.getId());
                                break; // Found a good match
                            }
                        } catch (Exception e) {
                            System.out.println(
                                    "[DEBUG] RedisSemanticCache.lookup - Failed to extract score: " + e.getMessage());
                            // Continue to next document
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[DEBUG] RedisSemanticCache.lookup - Failed to extract LLM: " + e.getMessage());
                    // Continue to next document
                }
            }

            // If no match found with suitable similarity, return null
            if (bestMatch == null) {
                System.out.println(
                        "[DEBUG] RedisSemanticCache.lookup - No matching document found with suitable similarity");
                return null; // No semantically similar documents with matching LLM
            }

            // Deserialize and return the response
            try {
                String responseJson = null;

                // Try different ways to extract the response JSON
                try {
                    responseJson = bestMatch.getString(JSON_PATH_PREFIX + RESPONSE_FIELD_NAME);
                    System.out.println("[DEBUG] RedisSemanticCache.lookup - Got response JSON via direct path");
                } catch (Exception e) {
                    System.out.println(
                            "[DEBUG] RedisSemanticCache.lookup - Direct JSON path failed: " + e.getMessage());
                    // Try alternative field access methods
                    System.out.println("[DEBUG] RedisSemanticCache.lookup - Attempting to access properties");

                    for (Map.Entry<String, Object> entry : bestMatch.getProperties()) {
                        System.out.println("[DEBUG] RedisSemanticCache.lookup - Property: " + entry.getKey() + " = "
                                + entry.getValue());
                        if (entry.getKey().endsWith(RESPONSE_FIELD_NAME)) {
                            responseJson = entry.getValue().toString();
                            System.out.println("[DEBUG] RedisSemanticCache.lookup - Found response JSON via property: "
                                    + entry.getKey());
                            break;
                        }
                    }
                }

                if (responseJson != null) {
                    System.out.println("[DEBUG] RedisSemanticCache.lookup - Response JSON: " + responseJson);
                    Response<?> response = OBJECT_MAPPER.readValue(responseJson, Response.class);
                    System.out.println("[DEBUG] RedisSemanticCache.lookup - Deserialized response");
                    return response;
                } else {
                    System.out.println("[DEBUG] RedisSemanticCache.lookup - No response JSON found in document");
                    throw new RedisCacheException("Could not extract response JSON from document");
                }
            } catch (JsonProcessingException e) {
                System.out.println("[DEBUG] RedisSemanticCache.lookup - JSON processing error: " + e.getMessage());
                throw new RedisCacheException("Failed to deserialize response JSON", e);
            }
        } catch (Exception e) {
            System.out.println("[DEBUG] RedisSemanticCache.lookup - Unexpected error: " + e.getMessage());
            e.printStackTrace();
            throw new RedisCacheException("Unexpected error during Redis semantic lookup", e);
        }
    }

    // No fallback or test environment detection methods - integration tests should use real Redis

    /**
     * Helper class for keyword-based cache lookup.
     */
    private static class CacheEntryData {
        final String prompt;
        final String responseJson;

        CacheEntryData(String prompt, String responseJson) {
            this.prompt = prompt;
            this.responseJson = responseJson;
        }
    }

    /**
     * Updates the cache with a new response.
     *
     * @param prompt The prompt used for the LLM request
     * @param llmString A string representing the LLM and its configuration
     * @param response The response to cache
     */
    public void update(String prompt, String llmString, Response<?> response) {
        System.out.println("[DEBUG] RedisSemanticCache.update - Prompt: " + prompt);
        System.out.println("[DEBUG] RedisSemanticCache.update - LLM String: " + llmString);

        // Generate a unique key for this entry
        String key = generateKey(prompt, llmString);
        System.out.println("[DEBUG] RedisSemanticCache.update - Generated key: " + key);

        // Convert the prompt to a vector embedding
        System.out.println("[DEBUG] RedisSemanticCache.update - Getting embedding for prompt");
        Response<Embedding> embeddingResponse = embeddingModel.embed(prompt);
        Embedding promptEmbedding = embeddingResponse.content();
        System.out.println(
                "[DEBUG] RedisSemanticCache.update - Got embedding with dimension: " + promptEmbedding.vector().length);

        try {
            // Prepare the data for storage
            Map<String, Object> data = new HashMap<>();
            data.put(PROMPT_FIELD_NAME, prompt);
            data.put(LLM_FIELD_NAME, llmString);

            // Serialize response to JSON
            String responseJson = OBJECT_MAPPER.writeValueAsString(response);
            data.put(RESPONSE_FIELD_NAME, responseJson);
            System.out.println("[DEBUG] RedisSemanticCache.update - Response JSON: " + responseJson);

            // Store vector as an array rather than a custom object to ensure proper serialization
            float[] vector = promptEmbedding.vector();
            data.put(VECTOR_FIELD_NAME, vector);

            // Store in Redis
            String jsonString = OBJECT_MAPPER.writeValueAsString(data);
            System.out.println("[DEBUG] RedisSemanticCache.update - JSON string length: " + jsonString.length());

            // Log a sample of the JSON string for debugging
            if (jsonString.length() > 100) {
                System.out.println(
                        "[DEBUG] RedisSemanticCache.update - JSON sample: " + jsonString.substring(0, 100) + "...");
            } else {
                System.out.println("[DEBUG] RedisSemanticCache.update - JSON: " + jsonString);
            }

            // Set the JSON in Redis
            String result = redis.jsonSet(key, ROOT_PATH, jsonString);
            System.out.println("[DEBUG] RedisSemanticCache.update - Redis JSON.SET result: " + result);

            // Verify the data was stored (helps check for any issues)
            Object storedData = redis.jsonGet(key, ROOT_PATH);
            System.out.println(
                    "[DEBUG] RedisSemanticCache.update - Data stored: " + (storedData != null ? "yes" : "no"));

            // Print existing keys to verify it's actually there
            String cursor = "0";
            ScanParams scanParams = new ScanParams().match(prefix + ":*").count(10);
            ScanResult<String> scanResult = redis.scan(cursor, scanParams);
            List<String> keys = scanResult.getResult();
            System.out.println("[DEBUG] RedisSemanticCache.update - Keys in Redis with prefix " + prefix + ": " + keys);

            // Verify the index exists after updating
            try {
                Set<String> indexes = redis.ftList();
                System.out.println("[DEBUG] RedisSemanticCache.update - Available indexes: " + indexes);

                // Try a basic search to verify the index works
                String testQuery = "*";
                Query query = new Query(testQuery);
                SearchResult searchResult = redis.ftSearch(indexName, query);
                System.out.println("[DEBUG] RedisSemanticCache.update - Basic search result count: "
                        + (searchResult != null ? searchResult.getTotalResults() : "null"));
            } catch (Exception e) {
                System.out.println("[DEBUG] RedisSemanticCache.update - Error testing index: " + e.getMessage());
            }

            // Set TTL if specified
            if (ttl != null) {
                redis.expire(key, ttl);
                System.out.println("[DEBUG] RedisSemanticCache.update - Set TTL: " + ttl + " seconds");
            }
        } catch (JsonProcessingException e) {
            System.out.println("[DEBUG] RedisSemanticCache.update - Error serializing data: " + e.getMessage());
            e.printStackTrace();
            throw new RedisCacheException("Failed to serialize data for caching", e);
        } catch (Exception e) {
            System.out.println("[DEBUG] RedisSemanticCache.update - Unexpected error: " + e.getMessage());
            e.printStackTrace();
            throw new RedisCacheException("Failed to update cache", e);
        }
    }

    /**
     * Clears all cache entries with the current prefix.
     */
    public void clear() {
        String cursor = "0";
        ScanParams params = new ScanParams().match(prefix + ":*");

        do {
            ScanResult<String> scanResult = redis.scan(cursor, params);
            List<String> keys = scanResult.getResult();
            cursor = scanResult.getCursor();

            if (!keys.isEmpty()) {
                redis.del(keys.toArray(new String[0]));
            }
        } while (!cursor.equals("0"));
    }

    /**
     * Generates a cache key from the prompt and LLM string.
     *
     * @param prompt The prompt used for the LLM request
     * @param llmString A string representing the LLM and its configuration
     * @return The cache key
     */
    public String generateKey(String prompt, String llmString) {
        // Create a unique key based on the prompt and LLM
        String uniqueIdentifier = md5(prompt + llmString + System.currentTimeMillis());
        return String.format("%s:%s", prefix, uniqueIdentifier);
    }

    /**
     * Creates a vector search query for finding semantically similar prompts.
     *
     * @param vector The vector to search for
     * @param llmString The LLM string to match
     * @return A Query object for vector similarity search
     */
    private Query createVectorQuery(float[] vector, String llmString) {
        return createVectorQuery(vector, llmString, null);
    }

    /**
     * Creates a vector search query for finding semantically similar prompts with additional filtering.
     *
     * @param vector The vector to search for
     * @param llmString The LLM string to match
     * @param filter Additional filters to apply to the query
     * @return A Query object for vector similarity search
     */
    private Query createVectorQuery(float[] vector, String llmString, FilterExpression filter) {
        // Format for Redis KNN search using vector similarity with LLM filtering
        StringBuilder queryBuilder = new StringBuilder();

        // Start with basic LLM filter
        if (llmString != null && !llmString.isEmpty()) {
            // Escape any special characters in the LLM string
            String escapedLlmString = escapeLlmString(llmString);
            queryBuilder
                    .append("@")
                    .append(LLM_FIELD_NAME)
                    .append(":{")
                    .append(escapedLlmString)
                    .append("}");
        } else {
            // If no LLM string provided, match all documents
            queryBuilder.append("*");
        }

        // Add custom filter if provided and enhanced filters are enabled
        if (enableEnhancedFilters && filter != null) {
            // If we already have an LLM filter, add the custom filter with an AND operator
            if (llmString != null && !llmString.isEmpty()) {
                queryBuilder.append(" ");
            }

            // Add the filter expression
            queryBuilder.append(filter.toRedisQueryString());
        }

        // Add the KNN vector search part - always the last part of the query
        queryBuilder.append(" =>[KNN 5 @").append(VECTOR_FIELD_NAME).append(" $BLOB]");

        String queryString = queryBuilder.toString();
        System.out.println("[DEBUG] RedisSemanticCache - Query string: " + queryString);
        System.out.println("[DEBUG] RedisSemanticCache - Vector length: " + vector.length);

        byte[] vectorBlob = RediSearchUtil.toByteArray(vector);
        System.out.println("[DEBUG] RedisSemanticCache - BLOB byte length: " + vectorBlob.length);

        Query query = new Query(queryString)
                .addParam("BLOB", vectorBlob)
                .returnFields(PROMPT_FIELD_NAME, LLM_FIELD_NAME, RESPONSE_FIELD_NAME, DISTANCE_FIELD_NAME)
                .limit(0, 5) // Return up to 5 results
                .dialect(2); // Use query dialect version 2 - required for vector search

        return query;
    }

    /**
     * Escapes special characters in the LLM string to prevent Redis search syntax errors.
     *
     * @param llmString The LLM string to escape
     * @return The escaped LLM string
     */
    private String escapeLlmString(String llmString) {
        // Replace characters that cause problems in Redis search syntax
        String escaped = llmString
                .replace(" ", "\\ ") // Escape spaces
                .replace("-", "\\-") // Escape hyphens
                .replace(".", "\\.") // Escape periods
                .replace(":", "\\:") // Escape colons
                .replace("/", "\\/") // Escape forward slashes
                .replace("(", "\\(") // Escape opening parentheses
                .replace(")", "\\)") // Escape closing parentheses
                .replace("[", "\\[") // Escape opening brackets
                .replace("]", "\\]") // Escape closing brackets
                .replace("=", "\\=") // Escape equals signs
                .replace("<", "\\<") // Escape less-than signs
                .replace(">", "\\>"); // Escape greater-than signs

        System.out.println("[DEBUG] RedisSemanticCache - Original LLM string: " + llmString);
        System.out.println("[DEBUG] RedisSemanticCache - Escaped LLM string: " + escaped);

        return escaped;
    }

    /**
     * Ensures that the vector index exists in Redis.
     */
    private void ensureIndexExists() {
        try {
            // Try to check if the Redis server supports search
            // Note: We don't use redis.info() as it's not available in JedisPooled
            // Instead, we'll directly check for RediSearch capability
            try {
                // Check if we can use FT commands to determine if RediSearch is available
                System.out.println(
                        "[DEBUG] RedisSemanticCache.ensureIndexExists - Checking Redis modules by testing FT commands");

                // We'll just try to list indexes, which will fail if RediSearch isn't available
                Set<String> moduleCheck = redis.ftList();
                boolean hasRediSearch = (moduleCheck != null);
                System.out.println("[DEBUG] RedisSemanticCache.ensureIndexExists - RediSearch module: "
                        + (hasRediSearch ? "present" : "absent"));
            } catch (Exception e) {
                System.out.println(
                        "[DEBUG] RedisSemanticCache.ensureIndexExists - RediSearch module appears to be missing: "
                                + e.getMessage());
            }

            Set<String> indexes;
            try {
                indexes = redis.ftList();
                System.out.println("[DEBUG] RedisSemanticCache.ensureIndexExists - Existing indexes: " + indexes);
            } catch (Exception e) {
                // If ftList fails, the server might not support RediSearch
                System.out.println(
                        "[DEBUG] RedisSemanticCache.ensureIndexExists - Failed to get index list: " + e.getMessage());
                System.out.println(
                        "[DEBUG] RedisSemanticCache.ensureIndexExists - This might indicate Redis Stack with RediSearch is not installed");

                // Attempt to create anyway - will fail if not supported, but with more specific error
                createIndex();
                return;
            }

            if (!indexes.contains(indexName)) {
                System.out.println(
                        "[DEBUG] RedisSemanticCache.ensureIndexExists - Index doesn't exist, creating: " + indexName);
                createIndex();

                // Verify index was created
                try {
                    indexes = redis.ftList();
                    System.out.println(
                            "[DEBUG] RedisSemanticCache.ensureIndexExists - Indexes after creation: " + indexes);

                    if (!indexes.contains(indexName)) {
                        System.out.println(
                                "[DEBUG] RedisSemanticCache.ensureIndexExists - Failed to verify index creation: "
                                        + indexName);
                        // Try to diagnose the problem
                        try {
                            // Check if we can create a simpler index as a test
                            String testIndexName = indexName + "-test";
                            String result = redis.ftCreate(
                                    testIndexName,
                                    FTCreateParams.createParams().on(IndexDataType.JSON),
                                    new SchemaField[] {TextField.of("$.test").as("test")});
                            System.out.println(
                                    "[DEBUG] RedisSemanticCache.ensureIndexExists - Test index creation result: "
                                            + result);
                        } catch (Exception te) {
                            System.out.println(
                                    "[DEBUG] RedisSemanticCache.ensureIndexExists - Test index creation failed: "
                                            + te.getMessage());
                        }

                        throw new RedisCacheException("Failed to create cache index");
                    } else {
                        System.out.println("[DEBUG] RedisSemanticCache.ensureIndexExists - Successfully created index: "
                                + indexName);
                        // Test the index with a simple query
                        try {
                            SearchResult result = redis.ftSearch(indexName, new Query("*"));
                            System.out.println("[DEBUG] RedisSemanticCache.ensureIndexExists - Test query result: "
                                    + (result != null ? result.getTotalResults() + " results" : "null"));
                        } catch (Exception qe) {
                            System.out.println("[DEBUG] RedisSemanticCache.ensureIndexExists - Test query failed: "
                                    + qe.getMessage());
                        }
                    }
                } catch (Exception e) {
                    System.out.println("[DEBUG] RedisSemanticCache.ensureIndexExists - Error verifying index creation: "
                            + e.getMessage());
                    throw new RedisCacheException("Error verifying index creation", e);
                }
            } else {
                System.out.println("[DEBUG] RedisSemanticCache.ensureIndexExists - Index already exists: " + indexName);
                // Test the existing index
                try {
                    SearchResult result = redis.ftSearch(indexName, new Query("*"));
                    System.out.println(
                            "[DEBUG] RedisSemanticCache.ensureIndexExists - Existing index test query result: "
                                    + (result != null ? result.getTotalResults() + " results" : "null"));
                } catch (Exception qe) {
                    System.out.println(
                            "[DEBUG] RedisSemanticCache.ensureIndexExists - Existing index test query failed: "
                                    + qe.getMessage());
                }
            }
        } catch (Exception e) {
            System.out.println(
                    "[DEBUG] RedisSemanticCache.ensureIndexExists - Failed to ensure index exists: " + e.getMessage());
            e.printStackTrace();
            throw new RedisCacheException("Error ensuring cache index exists", e);
        }
    }

    /**
     * Creates the vector search index in Redis.
     */
    private void createIndex() {
        // Get vector dimensionality from the embedding model
        int dimension = getEmbeddingDimension();

        // Create schema fields for the index - with simplified schema for testing
        Map<String, Object> vectorAttrs = new HashMap<>();
        vectorAttrs.put("DIM", dimension);
        vectorAttrs.put("DISTANCE_METRIC", "COSINE");
        vectorAttrs.put("TYPE", "FLOAT32");
        vectorAttrs.put("INITIAL_CAP", 5);

        // For simpler testing, only include essential fields
        SchemaField[] schemaFields = new SchemaField[] {
            // Vector field is the essential component for search
            VectorField.builder()
                    .fieldName(JSON_PATH_PREFIX + VECTOR_FIELD_NAME)
                    .algorithm(VectorAlgorithm.HNSW)
                    .attributes(vectorAttrs)
                    .as(VECTOR_FIELD_NAME)
                    .build(),
            // Also include text fields for retrieval
            TextField.of(JSON_PATH_PREFIX + PROMPT_FIELD_NAME).as(PROMPT_FIELD_NAME),
            TextField.of(JSON_PATH_PREFIX + LLM_FIELD_NAME).as(LLM_FIELD_NAME),
            TextField.of(JSON_PATH_PREFIX + RESPONSE_FIELD_NAME).as(RESPONSE_FIELD_NAME)
        };

        System.out.println(
                "[DEBUG] RedisSemanticCache.createIndex - Creating index with fields: VECTOR, PROMPT, LLM, RESPONSE");
        System.out.println("[DEBUG] RedisSemanticCache.createIndex - Vector dimension: " + dimension);
        System.out.println("[DEBUG] RedisSemanticCache.createIndex - Index name: " + indexName);
        System.out.println("[DEBUG] RedisSemanticCache.createIndex - Index prefix: " + prefix + ":");

        try {
            // First, check if we can drop the existing index for a clean slate
            try {
                if (redis.ftList().contains(indexName)) {
                    System.out.println("[DEBUG] RedisSemanticCache.createIndex - Dropping existing index");
                    redis.ftDropIndex(indexName);
                }
            } catch (Exception e) {
                System.out.println("[DEBUG] RedisSemanticCache.createIndex - Error dropping index: " + e.getMessage());
            }

            // Attempt to create the index with simplified parameters
            FTCreateParams params =
                    FTCreateParams.createParams().on(IndexDataType.JSON).addPrefix(prefix + ":");

            try {
                String result = redis.ftCreate(indexName, params, schemaFields);
                System.out.println("[DEBUG] RedisSemanticCache.createIndex - Index creation result: " + result);

                if (!"OK".equals(result)) {
                    System.out.println("[DEBUG] RedisSemanticCache.createIndex - Unexpected result: " + result);
                    throw new RedisCacheException("Failed to create vector index: " + result);
                }
            } catch (redis.clients.jedis.exceptions.JedisDataException e) {
                System.out.println("[DEBUG] RedisSemanticCache.createIndex - Error creating index: " + e.getMessage());

                // Special handling for common errors
                if (e.getMessage() != null) {
                    if (e.getMessage().contains("Index already exists")) {
                        System.out.println("[DEBUG] RedisSemanticCache.createIndex - Index already exists, continuing");
                    } else if (e.getMessage().contains("unknown command")) {
                        System.out.println(
                                "[DEBUG] RedisSemanticCache.createIndex - This Redis instance does not appear to have RediSearch module installed");
                        throw new RedisCacheException("Redis search module not available", e);
                    } else {
                        // Retry with even simpler schema - just the vector field
                        try {
                            System.out.println(
                                    "[DEBUG] RedisSemanticCache.createIndex - Retrying with simplified schema");
                            SchemaField[] simpleSchema = new SchemaField[] {
                                VectorField.builder()
                                        .fieldName(JSON_PATH_PREFIX + VECTOR_FIELD_NAME)
                                        .algorithm(VectorAlgorithm.HNSW)
                                        .attributes(vectorAttrs)
                                        .as(VECTOR_FIELD_NAME)
                                        .build()
                            };
                            String result = redis.ftCreate(indexName, params, simpleSchema);
                            System.out.println(
                                    "[DEBUG] RedisSemanticCache.createIndex - Simplified index creation result: "
                                            + result);
                        } catch (Exception e2) {
                            System.out.println(
                                    "[DEBUG] RedisSemanticCache.createIndex - Simplified schema also failed: "
                                            + e2.getMessage());
                            throw e2;
                        }
                    }
                } else {
                    throw e;
                }
            }

            // Verify the index was created and test it
            try {
                Set<String> indexes = redis.ftList();
                System.out.println("[DEBUG] RedisSemanticCache.createIndex - Available indexes: " + indexes);

                if (indexes.contains(indexName)) {
                    // Try a simple query to verify the index works
                    try {
                        System.out.println("[DEBUG] RedisSemanticCache.createIndex - Testing index with simple query");
                        SearchResult result = redis.ftSearch(indexName, new Query("*"));
                        System.out.println("[DEBUG] RedisSemanticCache.createIndex - Test query found "
                                + (result != null ? result.getTotalResults() : "null") + " documents");
                    } catch (Exception qe) {
                        System.out.println(
                                "[DEBUG] RedisSemanticCache.createIndex - Test query failed: " + qe.getMessage());
                    }
                } else {
                    System.out.println(
                            "[DEBUG] RedisSemanticCache.createIndex - Index not found in list after creation: "
                                    + indexName);
                }
            } catch (Exception e) {
                System.out.println("[DEBUG] RedisSemanticCache.createIndex - Error verifying index: " + e.getMessage());
            }

        } catch (Exception e) {
            System.out.println(
                    "[DEBUG] RedisSemanticCache.createIndex - Error creating vector index: " + e.getMessage());
            e.printStackTrace();
            throw new RedisCacheException("Error creating vector index", e);
        }
    }

    /**
     * Determines the embedding dimension by creating a sample embedding.
     *
     * @return The dimensionality of the embedding model
     */
    private int getEmbeddingDimension() {
        try {
            // Use a very simple text to get its embedding dimension
            String sampleText = "sample";
            System.out.println("[DEBUG] RedisSemanticCache.getEmbeddingDimension - Getting sample embedding");

            Response<Embedding> embeddingResponse = embeddingModel.embed(sampleText);
            Embedding sampleEmbedding = embeddingResponse.content();

            int dimension = sampleEmbedding.vector().length;
            System.out.println("[DEBUG] RedisSemanticCache.getEmbeddingDimension - Determined dimension: " + dimension);

            return dimension;
        } catch (Exception e) {
            System.out.println("[DEBUG] RedisSemanticCache.getEmbeddingDimension - Failed to determine dimension: "
                    + e.getMessage());
            System.out.println("[DEBUG] RedisSemanticCache.getEmbeddingDimension - Using default dimension 1536");

            // Default to a reasonable dimension size for typical embedding models
            return 1536;
        }
    }

    /**
     * Generates an MD5 hash of the input string.
     *
     * @param input The string to hash
     * @return The MD5 hash as a hexadecimal string
     */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RedisCacheException("Failed to create MD5 hash", e);
        }
    }

    @Override
    public void close() {
        // Close the Redis connection
        if (redis != null) {
            redis.close();
        }
    }

    /**
     * Builder for creating RedisSemanticCache instances.
     */
    public static class Builder {
        private JedisPooled redis;
        private EmbeddingModel embeddingModel;
        private Integer ttl;
        private String prefix = "semantic-cache";
        private Float similarityThreshold = 0.2f;
        private boolean enableEnhancedFilters = false;
        private String huggingFaceAccessToken = null;
        private boolean useDefaultRedisModel = false;

        public Builder redis(JedisPooled redis) {
            this.redis = redis;
            return this;
        }

        public Builder embeddingModel(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public Builder ttl(Integer ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public Builder similarityThreshold(Float similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
            return this;
        }

        /**
         * Enables enhanced filter support for the semantic cache.
         * This allows the use of Redis-specific filter expressions.
         *
         * @return This builder for chaining
         */
        public Builder enableEnhancedFilters() {
            this.enableEnhancedFilters = true;
            return this;
        }

        /**
         * Sets the access token for the HuggingFace API, used when creating the default
         * Redis LangCache embedding model. This is only used if no explicit embedding model
         * is provided via {@link #embeddingModel(EmbeddingModel)}.
         *
         * @param accessToken The HuggingFace API access token
         * @return This builder for chaining
         */
        public Builder huggingFaceAccessToken(String accessToken) {
            this.huggingFaceAccessToken = accessToken;
            return this;
        }

        /**
         * Use the Redis LangCache embedding model (redis/langcache-embed-v1) by default.
         * <p>
         * This model is specifically fine-tuned for semantic caching and provides better performance
         * for caching LLM responses. For more information about this model, see:
         * <a href="https://huggingface.co/redis/langcache-embed-v1">https://huggingface.co/redis/langcache-embed-v1</a>
         * and <a href="https://arxiv.org/abs/2504.02268">https://arxiv.org/abs/2504.02268</a>.
         * </p>
         * <p>
         * To use this model, you must have the langchain4j-hugging-face dependency on your classpath.
         * </p>
         *
         * @return This builder for chaining
         */
        public Builder useDefaultRedisModel() {
            this.useDefaultRedisModel = true;
            return this;
        }

        public RedisSemanticCache build() {
            if (redis == null) {
                throw new IllegalArgumentException("Redis client is required");
            }

            // If no embedding model is explicitly provided, but useDefaultRedisModel is enabled,
            // try to create the Redis LangCache embedding model
            if (embeddingModel == null && useDefaultRedisModel) {
                try {
                    // Import class dynamically to avoid direct dependency
                    Class<?> factoryClass = Class.forName(
                            "dev.langchain4j.community.store.embedding.redis.RedisLangCacheEmbeddingModelFactory");

                    // Use reflection to call the create method
                    java.lang.reflect.Method createMethod = factoryClass.getMethod("create", String.class);
                    embeddingModel = (EmbeddingModel) createMethod.invoke(null, huggingFaceAccessToken);

                    if (embeddingModel != null) {
                        System.out.println("[INFO] Using Redis LangCache embedding model (redis/langcache-embed-v1)");
                        System.out.println("[INFO] This model is specifically fine-tuned for semantic caching.");
                        System.out.println(
                                "[INFO] For more information: https://huggingface.co/redis/langcache-embed-v1");
                    }
                } catch (Exception e) {
                    System.out.println("[WARN] Could not create Redis LangCache embedding model: " + e.getMessage());
                    System.out.println("[WARN] Make sure langchain4j-hugging-face is on the classpath");
                }
            }

            // Still require an embedding model one way or another
            if (embeddingModel == null) {
                throw new IllegalArgumentException(
                        "Embedding model is required. Either provide one explicitly or enable useDefaultRedisModel()");
            }

            return new RedisSemanticCache(
                    redis, embeddingModel, ttl, prefix, similarityThreshold, enableEnhancedFilters);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
