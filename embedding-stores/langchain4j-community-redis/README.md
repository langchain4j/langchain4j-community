# LangChain4j Community: Redis Integration

This module provides Redis integration for LangChain4j, offering the following features:

- Redis as a vector store for embeddings with filtering support
- Redis-based chat memory store for conversation history
- Redis-based caching for LLM responses (exact matching)
- Redis-based semantic caching using vector similarity
- Redis-based semantic routing for directing queries to appropriate handlers
- Redis-based session management for tracking conversation state
- Enhanced Redis filters for powerful, type-safe query expressions
- Redis-based embedding vector caching for high performance and cost efficiency

## Redis as an Embedding Store

The `RedisEmbeddingStore` class provides Redis-based vector storage and retrieval for embeddings. It uses Redis Stack with RediSearch for efficient vector similarity search.

### Features

- Support for exact match and vector similarity search
- Support for metadata filtering using Redis JSON
- Enhanced filter capabilities for complex queries (see section below)
- Both FLAT and HNSW indexing algorithms
- Configurable distance metrics (COSINE, IP, L2)

## Redis as a Chat Memory Store

The `RedisChatMemoryStore` class provides Redis-based storage for conversation history. It uses Redis JSON to store and retrieve messages.

## Redis for Session Management

The Redis module provides session managers for tracking and retrieving conversation state:

### Standard Session Manager

The `RedisStandardSessionManager` provides traditional chronological conversation history management. It stores conversation messages in Redis and allows retrieving recent messages by timestamp.

### Semantic Session Manager

The `RedisSemanticSessionManager` extends the standard manager with semantic search capabilities. It uses vector embeddings and Redis Vector Search to find contextually relevant messages based on semantic similarity, not just chronological order.

## Redis for LLM Caching

### Exact Matching Cache

The `RedisCache` class provides Redis-based caching for LLM responses using exact matching.

### Semantic Caching

The `RedisSemanticCache` class provides semantic caching for LLM responses using vector similarity. This allows finding semantically similar prompts and reusing their responses.

#### Redis LangCache Embedding Model

The Redis integration includes support for the `redis/langcache-embed-v1` embedding model, which is fine-tuned specifically for semantic caching applications. This model provides excellent performance for caching LLM responses by generating embeddings that are optimized for semantic similarity of prompts.

Key features:
- Specifically trained for semantic caching use cases
- 768-dimensional embeddings (smaller than OpenAI models)
- Available as a public model on Hugging Face
- Can be enabled with just a simple `.useDefaultRedisModel()` setting

For more information, see the [model page on Hugging Face](https://huggingface.co/redis/langcache-embed-v1) and the [research paper](https://arxiv.org/abs/2504.02268).

## Redis for Semantic Routing

The `RedisSemanticRouter` class provides semantic routing capabilities, allowing you to direct queries to appropriate handlers based on semantic similarity.

### Features

- Define routes with reference texts and distance thresholds
- Route text inputs to the most semantically similar routes
- Retrieve route metadata for additional context

## Spring Boot Integration

This module provides Spring Boot auto-configuration for all Redis features:

- `RedisEmbeddingStoreAutoConfiguration`: Auto-configuration for `RedisEmbeddingStore`
- `RedisCacheAutoConfiguration`: Auto-configuration for `RedisCache`
- `RedisSemanticCacheAutoConfiguration`: Auto-configuration for `RedisSemanticCache`
- `RedisSemanticRouterAutoConfiguration`: Auto-configuration for `RedisSemanticRouter`
- `RedisSessionManagerAutoConfiguration`: Auto-configuration for session managers

### Configuration Properties

#### Redis Embedding Store Properties

```properties
langchain4j.community.redis.embedding-store.host=localhost
langchain4j.community.redis.embedding-store.port=6379
langchain4j.community.redis.embedding-store.user=default
langchain4j.community.redis.embedding-store.password=password
langchain4j.community.redis.embedding-store.index-type=FLAT
langchain4j.community.redis.embedding-store.metric-type=COSINE
langchain4j.community.redis.embedding-store.vector-dim=1536
langchain4j.community.redis.embedding-store.index-name=my-index
langchain4j.community.redis.embedding-store.prefix=vector
```

#### Redis Cache Properties

```properties
langchain4j.community.redis.cache.enabled=true
langchain4j.community.redis.cache.ttl=3600
langchain4j.community.redis.cache.prefix=exact-cache
```

#### Redis Semantic Cache Properties

```properties
langchain4j.community.redis.semantic-cache.enabled=true
langchain4j.community.redis.semantic-cache.ttl=3600
langchain4j.community.redis.semantic-cache.prefix=semantic-cache
langchain4j.community.redis.semantic-cache.similarity-threshold=0.2
langchain4j.community.redis.semantic-cache.use-default-redis-model=true
langchain4j.community.redis.semantic-cache.hugging-face-access-token=your-optional-token
```

#### Redis Semantic Router Properties

```properties
langchain4j.community.redis.semantic-router.enabled=true
langchain4j.community.redis.semantic-router.prefix=semantic-router
langchain4j.community.redis.semantic-router.max-results=5
```

#### Redis Session Manager Properties

```properties
langchain4j.community.redis.session-manager.enabled=true
langchain4j.community.redis.session-manager.type=standard
langchain4j.community.redis.session-manager.name=session-manager
langchain4j.community.redis.session-manager.prefix=session
langchain4j.community.redis.session-manager.distance-threshold=0.3
```

## Examples

### Using RedisEmbeddingStore

```java
// Create Redis client
JedisPooled jedis = new JedisPooled("localhost", 6379);

// Create embedding store
RedisEmbeddingStore embeddingStore = RedisEmbeddingStore.builder()
    .jedisClient(jedis)
    .indexName("my-index")
    .dimension(1536)
    .build();

// Add embeddings
embeddingStore.add(Embedding.from(vector1), metadata1, id1);
embeddingStore.add(Embedding.from(vector2), metadata2, id2);

// Find similar embeddings
List<EmbeddingMatch<Metadata>> matches = embeddingStore.findRelevant(
    Embedding.from(queryVector), 5);
```

### Using Redis Session Managers

```java
// Create Redis client
JedisPooled jedis = new JedisPooled("localhost", 6379);

// Create standard session manager (chronological history)
RedisStandardSessionManager standardSession = RedisStandardSessionManager.builder()
    .redis(jedis)
    .name("standard-session")
    .sessionTag("user-123") // Optional: unique session identifier
    .build();

// Store conversation exchanges
standardSession.store("Hello, how can you help me?", "I'm an AI assistant. How can I assist you today?");
standardSession.store("Tell me about Redis.", "Redis is an in-memory data structure store used as a database, cache, and message broker.");

// Retrieve recent conversation (returns last 3 messages)
List<?> recentMessages = standardSession.getRecent(3, false);

// Create semantic session manager with embedding model
EmbeddingModel embeddingModel = new OpenAiEmbeddingModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("text-embedding-ada-002")
    .build();

RedisSemanticSessionManager semanticSession = RedisSemanticSessionManager.builder()
    .redis(jedis)
    .embeddingModel(embeddingModel)
    .name("semantic-session")
    .distanceThreshold(0.3)
    .build();

// Store conversation exchanges (same as standard session)
semanticSession.store("What databases work well with Java?", "Several databases work well with Java, including PostgreSQL, MySQL, MongoDB, and Redis.");
semanticSession.store("How do I connect to Redis from Java?", "You can use Jedis, Lettuce, or Redisson as Redis clients in Java applications.");

// Get messages relevant to a specific topic (semantic search)
List<?> redisMessages = semanticSession.getRelevant("Tell me about Redis connection", 2, false);
```

### Using RedisCache (Exact Matching)

```java
// Create Redis client
JedisPooled jedis = new JedisPooled("localhost", 6379);

// Create cache
RedisCache cache = RedisCache.builder()
    .redis(jedis)
    .ttl(3600) // 1 hour TTL
    .prefix("my-cache")
    .build();

// Look up cached response
Response<?> cachedResponse = cache.lookup(prompt, modelString);

if (cachedResponse == null) {
    // Get response from model and cache it
    Response<?> newResponse = model.generate(prompt);
    cache.update(prompt, modelString, newResponse);
    return newResponse;
} else {
    return cachedResponse;
}
```

### Using RedisSemanticCache

```java
// Create Redis client
JedisPooled jedis = new JedisPooled("localhost", 6379);

// Option 1: Use the Redis LangCache embedding model (redis/langcache-embed-v1)
// This model is fine-tuned specifically for semantic caching
RedisSemanticCache semanticCache = RedisSemanticCache.builder()
    .redis(jedis)
    .useDefaultRedisModel() // Uses redis/langcache-embed-v1 by default
    .huggingFaceAccessToken("your-optional-hf-token") // Optional, public models don't require it
    .ttl(3600) // 1 hour TTL
    .prefix("semantic-cache")
    .similarityThreshold(0.2f)
    .build();

// Option 2: Use a custom embedding model
EmbeddingModel embeddingModel = new OpenAiEmbeddingModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("text-embedding-ada-002")
    .build();

RedisSemanticCache semanticCacheWithCustomModel = RedisSemanticCache.builder()
    .redis(jedis)
    .embeddingModel(embeddingModel) // Provide your own model
    .ttl(3600) // 1 hour TTL
    .prefix("semantic-cache")
    .similarityThreshold(0.2f)
    .build();

// Look up cached response by semantic similarity
Response<?> cachedResponse = semanticCache.lookup(prompt, modelString);

if (cachedResponse == null) {
    // Get response from model and cache it
    Response<?> newResponse = model.generate(prompt);
    semanticCache.update(prompt, modelString, newResponse);
    return newResponse;
} else {
    return cachedResponse;
}
```

### Using RedisSemanticRouter

```java
// Create Redis client
JedisPooled jedis = new JedisPooled("localhost", 6379);

// Create embedding model
EmbeddingModel embeddingModel = new OpenAiEmbeddingModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("text-embedding-ada-002")
    .build();

// Create semantic router
RedisSemanticRouter router = RedisSemanticRouter.builder()
    .redis(jedis)
    .embeddingModel(embeddingModel)
    .prefix("semantic-router")
    .maxResults(5)
    .build();

// Define routes
Route customerRoute = Route.builder()
    .name("customer_support")
    .addReference("I need help with my account")
    .addReference("How do I reset my password?")
    .distanceThreshold(0.2)
    .addMetadata("department", "customer-service")
    .build();

Route technicalRoute = Route.builder()
    .name("technical_support")
    .addReference("I'm getting an error message")
    .addReference("The system is not working")
    .distanceThreshold(0.2)
    .addMetadata("department", "engineering")
    .build();

// Add routes to router
router.addRoute(customerRoute);
router.addRoute(technicalRoute);

// Route a query
List<RouteMatch> matches = router.route("I can't log into my account");

// Process the best match
if (!matches.isEmpty()) {
    RouteMatch bestMatch = matches.get(0);
    System.out.println("Routed to: " + bestMatch.getRouteName());
    System.out.println("Similarity: " + bestMatch.getDistance());
    System.out.println("Metadata: " + bestMatch.getMetadata());
}
```

## Spring Boot Example

```java
@SpringBootApplication
public class MyApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return new OpenAiEmbeddingModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName("text-embedding-ada-002")
            .build();
    }

    @Bean
    public LanguageModel languageModel() {
        return OpenAiLanguageModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName("gpt-3.5-turbo")
            .build();
    }
}
```

```properties
# Redis connection
langchain4j.community.redis.embedding-store.host=localhost
langchain4j.community.redis.embedding-store.port=6379

# Enable Redis features
langchain4j.community.redis.cache.enabled=true
langchain4j.community.redis.semantic-cache.enabled=true
langchain4j.community.redis.semantic-router.enabled=true
langchain4j.community.redis.session-manager.enabled=true
```

## Enhanced Redis Filters

This module provides a comprehensive, type-safe API for creating complex filter expressions that leverage Redis' advanced search capabilities.

### Filter Types

#### Tag Filters

```java
// Equality
RedisFilter.tag("category").equalTo("finance");

// Inequality
RedisFilter.tag("category").notEqualTo("finance");

// In set
RedisFilter.tag("category").in("finance", "economics", "investing");

// Not in set
RedisFilter.tag("category").notIn("sports", "entertainment");
```

#### Numeric Filters

```java
// Equality
RedisFilter.numeric("rating").equalTo(5);

// Comparisons
RedisFilter.numeric("price").greaterThan(100);
RedisFilter.numeric("price").greaterThanOrEqualTo(100);
RedisFilter.numeric("price").lessThan(500);
RedisFilter.numeric("price").lessThanOrEqualTo(500);

// Range 
RedisFilter.numeric("price").between(100, 500);

// Range with inclusivity control
RedisFilter.numeric("price").between(100, 500, RangeType.EXCLUSIVE);
RedisFilter.numeric("price").between(100, 500, RangeType.LEFT_INCLUSIVE);
RedisFilter.numeric("price").between(100, 500, RangeType.RIGHT_INCLUSIVE);
```

#### Text Filters

```java
// Exact match
RedisFilter.text("title").exactMatch("Investment Guide");

// Not exact match
RedisFilter.text("title").notExactMatch("Investment Guide");

// Contains (full-text search)
RedisFilter.text("content").contains("stock market");

// Pattern matching (with wildcards)
RedisFilter.text("title").matchesPattern("invest*");

// Fuzzy matching (with Levenshtein distance)
RedisFilter.text("query").fuzzyMatch("investmnet", 2);  // Matches "investment" with typo
```

#### Timestamp Filters

```java
// On a specific date (all day)
RedisFilter.timestamp("created_at").onDate(LocalDate.of(2023, 3, 17));

// At a specific time
RedisFilter.timestamp("updated_at").at(LocalDateTime.now().minusHours(1));

// Before a specific time
RedisFilter.timestamp("created_at").before(LocalDateTime.now());

// After a specific time
RedisFilter.timestamp("created_at").after(LocalDateTime.now().minusDays(7));

// Between two times
RedisFilter.timestamp("created_at").between(
    LocalDateTime.now().minusDays(7), 
    LocalDateTime.now()
);
```

#### Geo Filters

```java
// Within radius
RedisFilter.geo("location").withinRadius(-122.4194, 37.7749, 5, "km");

// Outside radius
RedisFilter.geo("location").outsideRadius(-122.4194, 37.7749, 5, "km");

// Valid units: "m" (meters), "km" (kilometers), "mi" (miles), "ft" (feet)
```

### Combining Filters

```java
// Combining filters with logical operators
FilterExpression combinedFilter = 
    RedisFilter.tag("category").equalTo("finance")
        .and(RedisFilter.numeric("rating").greaterThanOrEqualTo(4))
        .and(RedisFilter.text("description").contains("investment"))
        .and(RedisFilter.timestamp("created_at").after(LocalDateTime.now().minusDays(30)))
        .and(RedisFilter.geo("location").withinRadius(-122.4194, 37.7749, 5, "km"));

// Convert to LangChain4j Filter
Filter filter = new RedisFilterExpression(combinedFilter);

// Use with RedisEmbeddingStore
List<EmbeddingMatch<TextSegment>> results = embeddingStore.findRelevant(
    queryEmbedding, 
    10,  // maxResults
    filter,
    0.7  // minScore 
);
```

### Using with Redis Semantic Cache

```java
// Create a filter for recently created finance content
FilterExpression filterExpr = RedisFilter.tag("category").equalTo("finance")
    .and(RedisFilter.timestamp("created_at").after(LocalDateTime.now().minusDays(7)));

// Convert to LangChain4j Filter
Filter redisFilter = new RedisFilterExpression(filterExpr);

// Look up in cache
Response<?> response = semanticCache.lookup("Tell me about investment strategies", "gpt-4", redisFilter);
```

## Requirements

- Redis Stack >= 6.2.0 with RediSearch and RedisJSON modules
- Java 17 or higher## Redis-Based Embedding Cache

The Redis integration includes embedding caching capabilities, which can significantly improve performance and reduce costs when working with embedding models.

### Why Use Embedding Caching?

- **Cost Efficiency**: Reduce API calls to expensive embedding models
- **Improved Performance**: Avoid recomputing embeddings for frequently used text
- **Batch Optimization**: Only compute embeddings for cache misses in batch requests
- **Testing Support**: Record/playback capability for testing without API calls

### Features

- Redis-backed persistent caching of embedding vectors
- Redis JSON storage for efficient and structured data management
- Flexible metadata storage and retrieval with each embedding
- Powerful metadata filtering and search capabilities
- Time-to-live (TTL) expiration support (global and per-entry)
- Maximum cache size with LRU-like eviction policy
- Efficient batch operations using Redis pipelining
- Access statistics tracking (insertion time, last access, access count)
- Transparent integration with any embedding model
- Multi-level integration options (direct, builder, global)
- Testing support with record/playback modes

### Using Embedding Cache

#### Basic Usage

```java
// Create Redis client
JedisPooled jedis = new JedisPooled("localhost", 6379);

// Create embedding cache
RedisEmbeddingCache cache = RedisEmbeddingCacheBuilder.builder()
    .jedisClient(jedis)
    .keyPrefix("embedding-cache")
    .ttl(3600) // 1 hour TTL
    .maxCacheSize(10000) // Limit to 10k embeddings
    .build();

// Wrap your embedding model with cache
EmbeddingModel originalModel = new OpenAiEmbeddingModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("text-embedding-ada-002")
    .build();

EmbeddingModel cachedModel = CachedEmbeddingModelBuilder.builder()
    .delegate(originalModel)
    .cache(cache)
    .build();

// Use cached model just like regular model
Response<Embedding> response = cachedModel.embed("This will be cached");
```

#### Batch Operations

The embedding cache supports efficient batch operations using Redis pipelining:

```java
// Batch get multiple embeddings at once
List<String> texts = List.of(
    "First text to embed",
    "Second text to embed",
    "Third text to embed"
);

// Get all cached embeddings in a single network operation
Map<String, Embedding> cachedEmbeddings = cache.mget(texts);

// Check which texts are already cached
Map<String, Boolean> existsMap = cache.mexists(texts);
List<String> missingTexts = existsMap.entrySet().stream()
    .filter(entry -> !entry.getValue())
    .map(Map.Entry::getKey)
    .collect(Collectors.toList());

// Compute and store multiple embeddings at once
Map<String, Embedding> newEmbeddings = computeEmbeddings(missingTexts);
cache.mput(newEmbeddings);

// Remove multiple embeddings at once
List<String> textsToRemove = List.of("First text to embed", "Second text to embed");
Map<String, Boolean> removeResults = cache.mremove(textsToRemove);
```

These batch operations are automatically used by the `CachedEmbeddingModel` when processing multiple texts, providing efficient handling for batch embedding operations.

#### Redis JSON Implementation

The Redis Embedding Cache utilizes Redis JSON capabilities for efficient and structured storage:

- **Native JSON storage**: Uses Redis JSON (`jsonSet` and `jsonGet`) for storing embeddings and metadata
- **Structured data**: Maintains relationships between embeddings, metadata, and statistics
- **Efficient operations**: Redis JSON provides optimized storage and retrieval of structured data
- **Rich data types**: Supports complex metadata types like nested objects, arrays, and timestamps

The implementation uses a `CacheEntry` class that encapsulates:
- Original text
- Embedding vector
- Metadata map 
- Model name (if available)
- Insertion timestamp
- Last access timestamp
- Access count

This provides a comprehensive representation that preserves all relationships in a single Redis JSON document.

### Working with Metadata

The embedding cache supports storing and retrieving arbitrary metadata with each embedding:

```java
// Create a cache
RedisEmbeddingCache cache = RedisEmbeddingCacheBuilder.builder()
    .jedisClient(jedis)
    .keyPrefix("embedding-cache")
    .ttlSeconds(3600)
    .build();

// Store an embedding with metadata
Map<String, Object> metadata = new HashMap<>();
metadata.put("source", "news_article");
metadata.put("category", "finance");
metadata.put("created_at", Instant.now().toEpochMilli());
metadata.put("importance", 8.5);
metadata.put("tags", List.of("markets", "stocks", "economy"));

// Metadata is stored as JSON structure alongside the embedding
cache.put("News about the stock market", embedding, metadata);

// Use a custom TTL for important embeddings
cache.put("Critical financial news", embedding, metadata, 86400); // 24 hours TTL

// Retrieve embedding with its metadata
Optional<Map.Entry<Embedding, Map<String, Object>>> result = 
    cache.getWithMetadata("News about the stock market");

if (result.isPresent()) {
    Embedding retrievedEmbedding = result.get().getKey();
    Map<String, Object> retrievedMetadata = result.get().getValue();
    
    // Access metadata fields
    String source = (String) retrievedMetadata.get("source");
    List<String> tags = (List<String>) retrievedMetadata.get("tags");
}

// Search for embeddings by metadata
Map<String, Object> filter = new HashMap<>();
filter.put("category", "finance");
filter.put("importance", 8.5);

Map<String, Map.Entry<Embedding, Map<String, Object>>> matches = 
    cache.findByMetadata(filter, 10); // Limit to 10 results

// Batch operations with metadata
Map<String, Map.Entry<Embedding, Map<String, Object>>> batchEntries = new HashMap<>();
batchEntries.put("First text", Map.entry(embedding1, metadata1));
batchEntries.put("Second text", Map.entry(embedding2, metadata2));

cache.mputWithMetadata(batchEntries);

// Retrieve multiple embeddings with their metadata
List<String> textsToGet = List.of("First text", "Second text");
Map<String, Map.Entry<Embedding, Map<String, Object>>> batchResults = 
    cache.mgetWithMetadata(textsToGet);
```

The Redis JSON implementation preserves all complex types in metadata, including:
- Nested objects and maps
- Arrays and lists
- Timestamps and date/time values
- Numeric values (both integers and floating point)
- Boolean values

When using the embedding cache with a model, the model name is automatically stored as metadata:

```java
EmbeddingModel model = new OpenAiEmbeddingModel("text-embedding-ada-002");
CachedEmbeddingModel cachedModel = CachedEmbeddingModelBuilder.builder()
    .delegate(model)
    .cache(cache)
    .build();

Response<Embedding> response = cachedModel.embed("This will be cached");

// Later, retrieve the embedding with metadata
Optional<Map.Entry<Embedding, Map<String, Object>>> result = 
    cache.getWithMetadata("This will be cached");

// The metadata will include "modelName": "text-embedding-ada-002"
```

#### Using Builder Extensions 

Extension methods are available to simplify caching setup:

```java
// Create embedding model
EmbeddingModel originalModel = new OpenAiEmbeddingModel.builder()
    .apiKey(System.getenv("OPENAI_API_KEY"))
    .modelName("text-embedding-ada-002")
    .build();

// Add caching with extension method
EmbeddingModel cachedModel = CacheableEmbeddingModelBuilder.builder(originalModel)
    .cacheWithRedis("localhost", 6379)
    .ttl(3600) // Optional: 1 hour TTL
    .maxCacheSize(10000) // Optional: 10k embeddings max
    .keyPrefix("my-app-embeddings") // Optional: custom key prefix
    .build();
```

#### Global Cache Configuration

For application-wide caching, use global configuration:

```java
// Configure global cache once
EmbeddingModelCache.configureGlobalRedisCache("localhost", 6379);

// Wrap any model with the global cache
EmbeddingModel model1 = new OpenAiEmbeddingModel.builder().build();
EmbeddingModel cachedModel1 = EmbeddingModelCache.wrap(model1);

EmbeddingModel model2 = new HuggingFaceEmbeddingModel.builder().build();
EmbeddingModel cachedModel2 = EmbeddingModelCache.wrap(model2);
```

### Testing Support

The embedding cache includes testing support that allows recording embeddings during development and playing them back during tests, eliminating the need for external API calls:

```java
// For test recording (development phase)
EmbeddingModel model = new OpenAiEmbeddingModel.builder().build();
EmbeddingModel recordingModel = EmbeddingCacheTestingSupport.recordMode(model, "test-user-classification");

// In your code, use the recordingModel to compute & save embeddings

// For test playback (test phase)
EmbeddingModel playbackModel = EmbeddingCacheTestingSupport.playMode(model, "test-user-classification");

// In your test, use playbackModel which will replay saved embeddings
// without making real API calls
```

### Spring Boot Integration

Embedding cache is automatically configured with Spring Boot:

```properties
# Enable embedding cache
langchain4j.community.redis.embedding-cache.enabled=true
langchain4j.community.redis.embedding-cache.ttl=3600
langchain4j.community.redis.embedding-cache.max-cache-size=10000
langchain4j.community.redis.embedding-cache.prefix=embedding-cache
```

```java
@Service
public class MyService {
    // Automatically wrapped with cache if enabled
    private final EmbeddingModel embeddingModel;

    public MyService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
}
```