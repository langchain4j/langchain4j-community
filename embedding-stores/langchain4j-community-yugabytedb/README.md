# LangChain4j YugabyteDB Integration

This module provides integration between LangChain4j and YugabyteDB as a vector embedding store.

## Overview

YugabyteDB is a distributed SQL database that provides PostgreSQL-compatible APIs with horizontal scalability. This integration leverages YugabyteDB's support for the pgvector extension to store and search vector embeddings efficiently.

## Features

- **Vector Similarity Search**: Supports cosine similarity, Euclidean distance, and dot product distance metrics
- **Metadata Filtering**: Advanced filtering capabilities using JSONB operations
- **Horizontal Scalability**: Leverages YugabyteDB's distributed architecture for large-scale deployments
- **PostgreSQL Compatibility**: Uses familiar PostgreSQL syntax and features
- **Connection Pooling**: Built-in HikariCP connection pooling for optimal performance
- **Automatic Schema Management**: Optional automatic table and index creation

## Prerequisites

- YugabyteDB cluster with pgvector extension enabled
- Java 17 or later
- Maven or Gradle for dependency management

## Installation

Add the following dependency to your project:

### Maven
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-yugabytedb</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
```

### Gradle
```groovy
implementation 'dev.langchain4j:langchain4j-community-yugabytedb:${langchain4j.version}'
```

## Quick Start

### 1. Set up YugabyteDB Engine

```java
YugabyteDBEngine engine = YugabyteDBEngine.builder()
    .host("localhost")
    .port(5433)
    .database("yugabyte")
    .username("yugabyte")
    .password("password")
    .build();
```

### 2. Create Embedding Store

```java
EmbeddingStore<TextSegment> embeddingStore = YugabyteDBEmbeddingStore.builder()
    .engine(engine)
    .tableName("embeddings")
    .dimension(384) // Match your embedding model's dimension
    .distanceStrategy(YugabyteDBEmbeddingStore.DistanceStrategy.COSINE)
    .createTableIfNotExists(true)
    .build();
```

### 3. Add Embeddings

```java
EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

TextSegment segment = TextSegment.from("Hello, world!");
Embedding embedding = embeddingModel.embed(segment).content();

String id = embeddingStore.add(embedding, segment);
```

### 4. Search Similar Embeddings

```java
List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(EmbeddingSearchRequest.builder()
    .queryEmbedding(queryEmbedding)
    .maxResults(5)
    .minScore(0.7)
    .build()).matches();
```

## Configuration

### Connection Options

```java
YugabyteDBEngine engine = YugabyteDBEngine.builder()
    .host("localhost")
    .port(5433)
    .database("yugabyte")
    .username("yugabyte")
    .password("password")
    .schema("public")
    .useSsl(false)
    .maxPoolSize(10)
    .minPoolSize(5)
    .build();
```

### Embedding Store Options

```java
YugabyteDBEmbeddingStore store = YugabyteDBEmbeddingStore.builder()
    .engine(engine)
    .tableName("custom_embeddings")
    .schemaName("ai_schema")
    .contentColumn("text_content")
    .embeddingColumn("vector_data")
    .idColumn("embedding_id")
    .metadataColumn("metadata_json")
    .dimension(768)
    .distanceStrategy(DistanceStrategy.EUCLIDEAN)
    .createTableIfNotExists(true)
    .build();
```

## Distance Strategies

The integration supports three distance strategies:

- **COSINE**: Cosine similarity (default, good for normalized vectors)
- **EUCLIDEAN**: Euclidean (L2) distance
- **DOT_PRODUCT**: Dot product similarity

## Metadata Filtering

Advanced filtering using metadata properties:

```java
Filter filter = metadataKey("category").isEqualTo("technology")
    .and(metadataKey("year").isGreaterThan(2020));

List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(EmbeddingSearchRequest.builder()
    .queryEmbedding(queryEmbedding)
    .filter(filter)
    .maxResults(10)
    .build()).matches();
```

## Database Schema

The default table schema includes:

```sql
CREATE TABLE embeddings (
    id UUID PRIMARY KEY,
    content TEXT,
    metadata JSONB,
    embedding vector(384)
);

CREATE INDEX embeddings_embedding_idx 
ON embeddings USING ivfflat (embedding vector_cosine_ops);
```

## Performance Considerations

1. **Vector Dimensions**: Choose the appropriate dimension based on your embedding model
2. **Index Strategy**: YugabyteDB supports IVFFlat indexing for efficient vector search
3. **Connection Pooling**: Configure connection pool size based on your workload
4. **Sharding**: Leverage YugabyteDB's horizontal scaling for large datasets

## Best Practices

1. **Pre-compute Embeddings**: Generate embeddings offline when possible
2. **Batch Operations**: Use batch inserts for better performance
3. **Monitor Performance**: Use YugabyteDB's monitoring tools to optimize queries
4. **Connection Management**: Properly close connections and pools

## Troubleshooting

### Common Issues

1. **pgvector Extension Not Found**
   ```sql
   CREATE EXTENSION IF NOT EXISTS vector;
   ```

2. **Connection Pool Exhaustion**
   - Increase `maxPoolSize` in YugabyteDBEngine configuration
   - Ensure proper connection cleanup in your application

3. **Slow Vector Search**
   - Verify that vector indexes are created
   - Consider adjusting IVFFlat index parameters
   - Monitor YugabyteDB cluster performance

### Logging

Enable debug logging to troubleshoot issues:

```properties
logging.level.dev.langchain4j.community.store.embedding.yugabytedb=DEBUG
```

## Examples

For complete examples and advanced usage patterns, see the [langchain4j-examples](https://github.com/langchain4j/langchain4j-examples) repository.

## Contributing

Contributions are welcome! Please see the [Contributing Guide](../../CONTRIBUTING.md) for details.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](../../LICENSE) file for details.
