# OceanBase Vector Store for LangChain4j

This module implements an `EmbeddingStore` backed by an OceanBase database.

- [Product Documentation](https://www.oceanbase.com/docs/common-oceanbase-database-cn-1000000002826816)

The **OceanBase for LangChain4j** package provides a first-class experience for connecting to OceanBase instances from the LangChain4j ecosystem while providing the following benefits:

- **Simplified Vector Storage**: Utilize OceanBase's vector data types and indexing capabilities for efficient similarity searches.
- **Improved Metadata Handling**: Store metadata in JSON columns instead of strings, resulting in significant performance improvements.
- **Clear Separation**: Clearly separate table and extension creation, allowing for distinct permissions and streamlined workflows.
- **Better Integration with OceanBase**: Built-in methods to take advantage of OceanBase's advanced indexing and scalability capabilities.

## Quick Start

In order to use this library, you first need to go through the following steps:

1. [Install OceanBase Database](https://www.oceanbase.com/docs/common-oceanbase-database-cn-1000000002012734)
2. Create a database and user
3. Configure vector memory limits (if needed)

### Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-oceanbase</artifactId>
    <version>1.2.0-beta8-SNAPSHOT</version>
</dependency>
```

### Supported Java Versions

Java >= 17

## OceanBaseEmbeddingStore Usage

`OceanBaseEmbeddingStore` is used to store text embedded data and perform vector search. Instances can be created by configuring the provided `Builder`, which requires:

- A `DataSource` instance (connected to an OceanBase database)
- Table name
- Table configuration (optional, uses standard configuration by default)
- Exact search option (optional, uses approximate search by default)

Example usage:

```java
import dev.langchain4j.community.store.embedding.oceanbase.OceanBaseEmbeddingStore;
import dev.langchain4j.community.store.embedding.oceanbase.EmbeddingTable;
import dev.langchain4j.community.store.embedding.oceanbase.CreateOption;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;

import javax.sql.DataSource;
import java.util.*;

// Create a data source
DataSource dataSource = createDataSource(); // You need to implement this method

// Create a vector store
OceanBaseEmbeddingStore store = OceanBaseEmbeddingStore.builder(dataSource)
    .embeddingTable(
        EmbeddingTable.builder("my_embeddings")
            .vectorDimension(384)  // Set vector dimension
            .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
            .build())
    .build();

// Add embeddings
List<String> testTexts = Arrays.asList("apple", "banana", "car", "truck");
List<Embedding> embeddings = new ArrayList<>();
List<TextSegment> textSegments = new ArrayList<>();

for (String text : testTexts) {
    Map<String, String> metaMap = new HashMap<>();
    metaMap.put("category", text.length() <= 5 ? "fruit" : "vehicle");
    Metadata metadata = Metadata.from(metaMap);
    textSegments.add(TextSegment.from(text, metadata));
    embeddings.add(myEmbeddingModel.embed(text).content()); // Use your embedding model
}

// Batch add embeddings and text segments
List<String> ids = store.addAll(embeddings, textSegments);

// Search for similar vectors
EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
    .queryEmbedding(embeddings.get(0))  // Search for content similar to "apple"
    .maxResults(10)
    .minScore(0.7)
    .build();

List<EmbeddingMatch<TextSegment>> results = store.search(request).matches();

// Use metadata filtering
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;

// Search only in the "fruit" category
EmbeddingSearchRequest filteredRequest = EmbeddingSearchRequest.builder()
    .queryEmbedding(embeddings.get(0))
    .maxResults(10)
    .filter(MetadataFilterBuilder.metadataKey("category").isEqualTo("fruit"))
    .build();

List<EmbeddingMatch<TextSegment>> filteredResults = store.search(filteredRequest).matches();

// Remove embeddings
store.remove(ids.get(0));  // Remove a single vector
store.removeAll(Arrays.asList(ids.get(1), ids.get(2)));  // Remove multiple vectors
store.removeAll(MetadataFilterBuilder.metadataKey("category").isEqualTo("fruit"));  // Remove by metadata
store.removeAll();  // Remove all vectors
```

## EmbeddingTable Configuration

The `EmbeddingTable` class is used to configure the structure and creation options of the vector table:

```java
import dev.langchain4j.community.store.embedding.oceanbase.EmbeddingTable;
import dev.langchain4j.community.store.embedding.oceanbase.CreateOption;

// Basic configuration
EmbeddingTable table = EmbeddingTable.builder("my_embeddings")
    .vectorDimension(384)  // Set vector dimension
    .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
    .build();

// Advanced configuration
EmbeddingTable advancedTable = EmbeddingTable.builder("advanced_embeddings")
    .idColumn("custom_id")  // Custom ID column name
    .embeddingColumn("vector_data")  // Custom vector column name
    .textColumn("content")  // Custom text column name
    .metadataColumn("meta_info")  // Custom metadata column name
    .vectorDimension(768)  // Set vector dimension
    .vectorIndexName("idx_vector_search")  // Custom index name
    .distanceMetric("L2")  // Set distance metric (L2, IP, COSINE)
    .indexType("hnsw")  // Set index type (hnsw, flat)
    .createOption(CreateOption.CREATE_OR_REPLACE)  // Table creation option
    .build();
```

### Table Creation Options

The `CreateOption` enum provides the following options:

- `CREATE_NONE`: Do not create a table, assumes the table already exists
- `CREATE_IF_NOT_EXISTS`: Create the table if it does not exist (default)
- `CREATE_OR_REPLACE`: Create the table, replacing it if it exists

## Search Options

`OceanBaseEmbeddingStore` supports two search modes:

- **Approximate Search** (default): Faster but may not be 100% accurate
- **Exact Search**: Slower but 100% accurate

```java
// Use exact search
OceanBaseEmbeddingStore exactStore = OceanBaseEmbeddingStore.builder(dataSource)
    .embeddingTable("my_embeddings")
    .exactSearch(true)  // Enable exact search
    .build();
```

## Metadata Filtering

Complex metadata filtering conditions can be built using `MetadataFilterBuilder`:

```java
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;

// Basic filtering
EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
    .queryEmbedding(queryEmbedding)
    .filter(MetadataFilterBuilder.metadataKey("category").isEqualTo("fruit"))
    .build();

// Combined filtering
EmbeddingSearchRequest complexRequest = EmbeddingSearchRequest.builder()
    .queryEmbedding(queryEmbedding)
    .filter(
        MetadataFilterBuilder.metadataKey("category").isEqualTo("fruit")
            .and(MetadataFilterBuilder.metadataKey("color").isEqualTo("red"))
    )
    .build();
```

## Performance Optimization

For optimal performance, consider the following recommendations:

1. Adjust OceanBase's vector memory limit appropriately for large vector collections:
   ```sql
   ALTER SYSTEM SET ob_vector_memory_limit_percentage = 30;
   ```

2. Choose the index type that suits your use case:
   - `hnsw`: Suitable for most scenarios, provides a good balance of performance/accuracy
   - `flat`: Use when highest accuracy is required

3. Add embeddings in batches to improve performance:
   ```java
   store.addAll(embeddings, textSegments);
   ```

4. For frequent similarity searches, consider using approximate search mode (default).
