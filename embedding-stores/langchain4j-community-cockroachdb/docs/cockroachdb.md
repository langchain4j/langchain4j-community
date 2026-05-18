---
sidebar_position: 99
---

# CockroachDB

[CockroachDB](https://www.cockroachlabs.com/) is a distributed SQL database that
speaks the PostgreSQL wire protocol. Since v24.2 it ships a native `VECTOR`
column type, and since v25.2 it offers a distributed approximate nearest
neighbour index called **C-SPANN**. This module integrates both with
LangChain4j as:

- a vector `EmbeddingStore<TextSegment>` (`CockroachDbEmbeddingStore`)
- a `ChatMemoryStore` (`CockroachDbChatMemoryStore`)

The Java module mirrors the feature set of the official Python
[`langchain-cockroachdb`](https://github.com/cockroachdb/langchain-cockroachdb)
library where the Java equivalents exist.

## Version requirements

| Feature | Minimum CockroachDB version |
| --- | --- |
| `VECTOR(n)` column type | v24.2 |
| `CREATE VECTOR INDEX` (C-SPANN) | v25.2 |
| Row-level TTL via `ttl_expiration_expression` | v23.1 |

On CockroachDB v25.2, vector indexes are gated by a cluster setting. Enable it
once per cluster before creating a store with a `CSpannIndex`:

```sql
SET CLUSTER SETTING feature.vector_index.enabled = true;
```

## Maven

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-cockroachdb</artifactId>
    <version>${langchain4j-community.version}</version>
</dependency>
```

If you import the Community BOM, you can omit the version.

## Connecting

`CockroachDbEngine` wraps a `HikariDataSource`. You can build one from a
connection string or from individual fields.

```java
import dev.langchain4j.community.store.embedding.cockroachdb.CockroachDbEngine;

CockroachDbEngine engine = CockroachDbEngine.builder()
        .host("localhost")
        .port(26257)
        .database("defaultdb")
        .username("root")
        .password("")
        .sslMode("disable")
        .build();
```

The builder also accepts a full connection string. The Python-style
`cockroachdb://` scheme is rewritten to `jdbc:postgresql://` automatically,
so you can paste the same URL the Python library uses:

```java
CockroachDbEngine engine = CockroachDbEngine.fromConnectionString(
        "cockroachdb://root@localhost:26257/defaultdb?sslmode=disable");
```

If you already have a `DataSource`, use `CockroachDbEngine.from(dataSource)`.

## Vector store

A minimal vector store uses sequential scan (`NoIndex`), which is appropriate
for small datasets and tests:

```java
import dev.langchain4j.community.store.embedding.cockroachdb.CockroachDbEmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;

EmbeddingModel model = new AllMiniLmL6V2QuantizedEmbeddingModel();

CockroachDbEmbeddingStore store = CockroachDbEmbeddingStore.builder()
        .engine(engine)
        .dimension(model.dimension())
        .tableName("embeddings")
        .build();

TextSegment segment = TextSegment.from("Cockroaches are surprisingly resilient.");
Embedding embedding = model.embed(segment).content();
store.add(embedding, segment);
```

For production workloads on CockroachDB v25.2+, add a C-SPANN vector index:

```java
import dev.langchain4j.community.store.embedding.cockroachdb.index.CSpannIndex;

CockroachDbEmbeddingStore store = CockroachDbEmbeddingStore.builder()
        .engine(engine)
        .dimension(model.dimension())
        .vectorIndex(CSpannIndex.builder()
                .minPartitionSize(16)
                .maxPartitionSize(128)
                .build())
        .build();
```

The DDL emitted for the index is:

```sql
CREATE VECTOR INDEX IF NOT EXISTS embeddings_embedding_vector_idx
  ON public.embeddings (embedding)
  WITH (min_partition_size = 16, max_partition_size = 128);
```

C-SPANN picks the distance function from the query operator (`<=>` for cosine,
`<->` for L2, `<#>` for inner product), so `MetricType` is selected at query
time on the store, not bound to the index.

### Searching

`EmbeddingSearchRequest` works the same as in any other LangChain4j store:

```java
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;

EmbeddingSearchResult<TextSegment> result = store.search(
        EmbeddingSearchRequest.builder()
                .queryEmbedding(model.embed("resilience").content())
                .maxResults(5)
                .minScore(0.6)
                .build());

result.matches().forEach(m ->
        System.out.printf("%s (%.3f) %s%n", m.embeddingId(), m.score(), m.embedded().text()));
```

### Tuning C-SPANN at query time

CockroachDB exposes a session variable, `vector_search_beam_size`, that
controls the recall/latency tradeoff. Set it on the store builder to wrap
each search in a transaction that scopes the setting with `SET LOCAL`:

```java
CockroachDbEmbeddingStore store = CockroachDbEmbeddingStore.builder()
        .engine(engine)
        .dimension(model.dimension())
        .vectorIndex(CSpannIndex.builder().build())
        .searchBeamSize(32)
        .build();
```

Higher values trade latency for recall. The default beam size is decided by
CockroachDB if you leave the field unset.

### Metadata filtering

Metadata is stored in a JSONB column and filtered at query time using
LangChain4j `Filter` expressions:

```java
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;

EmbeddingSearchResult<TextSegment> result = store.search(
        EmbeddingSearchRequest.builder()
                .queryEmbedding(query)
                .maxResults(10)
                .filter(MetadataFilterBuilder.metadataKey("category").isEqualTo("biology")
                        .and(MetadataFilterBuilder.metadataKey("year").isGreaterThan(2020)))
                .build());
```

Comparison filters (`>`, `>=`, `<`, `<=`) cast the JSONB value to `numeric`.
Equality on strings compares JSON text. The filter key must contain only
alphanumeric characters, dots, underscores or hyphens.

### Multi-tenancy with a namespace column

To scope rows by tenant, add a `namespaceColumn` to the schema and configure a
namespace value on each store instance. The column is added as a prefix to the
C-SPANN index so per-tenant queries stay fast:

```java
CockroachDbEmbeddingStore tenantA = CockroachDbEmbeddingStore.builder()
        .engine(engine)
        .dimension(model.dimension())
        .namespaceColumn("tenant_id")
        .namespace("acme")
        .vectorIndex(CSpannIndex.builder().build())
        .build();
```

The generated index becomes `CREATE VECTOR INDEX ... ON embeddings (tenant_id, embedding)`,
and every read/write performed through this store is filtered to `tenant_id = 'acme'`.

### Optional full-text column

If you intend to combine vector search with full-text search later, enable a
generated `tsvector` column at table creation time. A GIN index is created
alongside it:

```java
CockroachDbEmbeddingStore store = CockroachDbEmbeddingStore.builder()
        .engine(engine)
        .dimension(model.dimension())
        .createTsvectorColumn(true)
        .build();
```

Hybrid (vector + FTS) query execution is not yet implemented; the column is
created so it can be used by application code or a future release.

## Chat memory

`CockroachDbChatMemoryStore` implements `ChatMemoryStore` and persists
serialised chat messages in a JSONB column ordered by insertion time:

```java
import dev.langchain4j.community.store.memory.chat.cockroachdb.CockroachDbChatMemoryStore;

CockroachDbChatMemoryStore memory = CockroachDbChatMemoryStore.builder()
        .engine(engine)
        .tableName("chat_memory")
        .build();
```

The schema is:

```sql
CREATE TABLE chat_memory (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id TEXT NOT NULL,
  message JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX chat_memory_session_idx ON chat_memory (session_id, created_at);
```

`updateMessages` replaces the full session inside a transaction, so partial
writes are not visible to readers.

### Row-level TTL

CockroachDB can expire rows automatically. Pass a `ttl` duration to enable
[row-level TTL](https://www.cockroachlabs.com/docs/stable/row-level-ttl) on
the chat memory table:

```java
import java.time.Duration;

CockroachDbChatMemoryStore memory = CockroachDbChatMemoryStore.builder()
        .engine(engine)
        .tableName("chat_memory")
        .ttl(Duration.ofDays(7))
        .ttlJobCron("@daily")
        .build();
```

The schema setup emits:

```sql
ALTER TABLE chat_memory SET (
  ttl_expiration_expression = $$(created_at + '7 days')$$,
  ttl_job_cron = '@daily'
);
```

To disable TTL on an existing table:

```java
memory.disableTtl();
```

## Retries

CockroachDB returns SQLSTATE `40001` when a transaction must be retried under
its default `SERIALIZABLE` isolation. The store wraps each unit of work in a
retry loop with exponential backoff and jitter (5 attempts by default,
starting at 100 ms, doubling up to 10 seconds). No additional configuration
is needed.

## Connection string formats

The following forms are all accepted by `CockroachDbEngine.fromConnectionString`:

| Form | Example |
| --- | --- |
| Python style | `cockroachdb://root@localhost:26257/defaultdb?sslmode=disable` |
| psycopg style | `cockroachdb+psycopg://user:pw@host:26257/db` |
| libpq style | `postgresql://user@host:26257/db` |
| JDBC style | `jdbc:postgresql://localhost:26257/defaultdb` |

For CockroachDB Cloud, use the connection string from the cluster console,
typically:

```
cockroachdb://USER:PASSWORD@HOST:26257/DATABASE?sslmode=verify-full
```

## Differences from the Python library

The Python `langchain-cockroachdb` library additionally provides a LangGraph
checkpointer (`CockroachDBSaver` / `AsyncCockroachDBSaver`). LangChain4j does
not yet have a LangGraph equivalent, so no Java port of that component is
included in this module.

The Python library also ships a `HybridSearchConfig` class for fusing vector
and FTS scores in application code. The Java module exposes the underlying
tsvector column but does not yet ship a hybrid query executor.
