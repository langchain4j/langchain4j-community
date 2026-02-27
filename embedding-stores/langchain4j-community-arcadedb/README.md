# LangChain4j ArcadeDB Embedding Store

An [ArcadeDB](https://arcadedb.com) implementation of the LangChain4j `EmbeddingStore`.

## Key Feature: Embedded / Zero-Config

Unlike most LangChain4j embedding stores that connect to external servers over the network, ArcadeDB runs **embedded in the same JVM** — zero network overhead, zero serialization cost, with persistence and native HNSW vector indexing.

No Docker, no server process, no connection string. Just a local directory path.

## Usage

### Maven

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-arcadedb</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
```

### Create a Store

```java
ArcadeDBEmbeddingStore store = ArcadeDBEmbeddingStore.builder()
        .databasePath("/tmp/my-embeddings")
        .dimension(384) // must match your embedding model
        .build();
```

### Add Embeddings

```java
Embedding embedding = embeddingModel.embed("some text").content();
TextSegment segment = TextSegment.from("some text", new Metadata().put("source", "docs"));
store.add(embedding, segment);
```

### Search

```java
EmbeddingSearchResult<TextSegment> result = store.search(
    EmbeddingSearchRequest.builder()
        .queryEmbedding(embeddingModel.embed("query").content())
        .maxResults(10)
        .minScore(0.7)
        .filter(metadataKey("source").isEqualTo("docs"))
        .build());
```

### Close

The store owns the database lifecycle. Close it when done:

```java
store.close();
```

## Configuration

| Parameter | Default | Description |
|---|---|---|
| `databasePath` | *(required)* | Path for the embedded database directory |
| `dimension` | 384 | Embedding vector dimension |
| `database` | — | Use an existing ArcadeDB `Database` instance instead of creating one |
| `typeName` | `"Document"` | Vertex type name for stored embeddings |
| `metadataPrefix` | `""` | Prefix for metadata properties on vertices |
| `m` | 16 | HNSW M parameter (max connections per node) |
| `ef` | 10 | HNSW ef parameter (search beam width) |
| `efConstruction` | 200 | HNSW efConstruction parameter (build-time beam width) |

## Supported Filters

All LangChain4j metadata filters are supported:

- `isEqualTo`, `isNotEqualTo`
- `isGreaterThan`, `isGreaterThanOrEqualTo`
- `isLessThan`, `isLessThanOrEqualTo`
- `isIn`, `isNotIn`
- `containsString`
- `and`, `or`, `not`

Metadata types: `String`, `Integer`, `Long`, `Float`, `Double`, `UUID` (stored as String).

## Architecture Notes

- **Storage**: Embeddings are stored as vertices in ArcadeDB's graph engine with an HNSW vector index for nearest-neighbor search.
- **Deletion**: Uses soft-delete (`deleted=true` flag) to preserve HNSW graph connectivity. Deleted vertices are excluded from search results via post-filtering.
- **Filter evaluation**: Metadata filters are evaluated in Java (not SQL) to ensure correct type handling across all numeric types, UUIDs, and logical operators.
