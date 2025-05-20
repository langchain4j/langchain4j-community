# CloudSQL for PostgreSQL Embedding Store for LangChain4j


This module implements `EmbeddingStore` backed by an CloudSQL for PostgreSQL database.

- [Product Documentation]([https://cloud.google.com/sql](https://cloud.google.com/sql/docs/postgres))

The **CloudSQL for LangChain4j** package provides a first class experience for connecting to
CloudSQL instances from the LangChain4j ecosystem while providing the following benefits:

- **Simplified & Secure Connections**: easily and securely create shared connection pools to connect to Google Cloud databases utilizing IAM for authorization and database authentication without needing to manage SSL certificates, configure firewall rules, or enable authorized networks.
- **Improved performance & Simplified management**: use a single-table schema can lead to faster query execution, especially for large collections.
- **Improved metadata handling**: store metadata in columns instead of JSON, resulting in significant performance improvements.
- **Clear separation**: clearly separate table and extension creation, allowing for distinct permissions and streamlined workflows.
- **Better integration with CloudSQL**: built-in methods to take advantage of CloudSQL's advanced indexing and scalability capabilities.


## Quick Start

In order to use this library, you first need to go through the following
steps:

1. [Select or create a Cloud Platform project.](https://console.cloud.google.com/project)
2. [Enable billing for your project.](https://cloud.google.com/billing/docs/how-to/modify-project#enable_billing_for_a_project)
3. [Enable the CloudSQL API.](https://console.cloud.google.com/flows/enableapi?apiid=sql.googleapis.com)
4. [Setup Authentication.](https://googleapis.dev/python/google-api-core/latest/auth.html)


### Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artificatId>langchain4j-community-cloud-sql-pg</artificatId>
    <version>1.1.0-beta7-SNAPSHOT</version>
</dependency>
```

### Supported Java Versions

Java >= 17


## PostgresEngine Usage

Instances of `PostgresEngine` can be created by configuring provided `Builder`. The `PostgresEngine` configures a connection pool to your Cloud SQL database,
enabling successful connections from your application and following industry best practices.
Securely connect to CloudSQL by using the built-in [Cloud SQL Connector for Java](https://github.com/GoogleCloudPlatform/cloud-sql-jdbc-socket-factor) using Builder
options:
 - project id
 - region
 - instance
 - database
 - user (optional)
 - password (optional)
 - IAM account email (optional)
 - ip-type (optional, default: "public")

For authentication there will be 3 possible configurations
- provided user and password will be used to login to the database
- provided IAM account email will be used to login to the database
- if neither the IAM account email or user and password are provided, the Engine will retrieve the account from the authenticated Google credential.

Connect to a Cloud SQL instance by specifying the host:
 - host
 - port (optional, default: 5432).
 - database
 - user
 - password

```java
import dev.langchain4j.engine.PostgresEngine;

    PostgresEngine engine = new PostgresEngine.Builder()
                .projectId("my-projectId")
                .region("my-region")
                .instance("my-instance")
                .database("my-database")
                .build();

```


### Create a Vector Store table
`PostgresEngine` provides `initVectorStoreTable` method to initialize the vector store table, it requires a `EmbeddingStoreConfig` object that can be configured with its provided `Builder` it requires the following:
- table name
- vector size
- schema name (optional, default: "public")
- content column name (optional, default: "content")
- embedding column name(optional, default:"embedding")
- id column name (optional, default:"langchain4j_id")
- metadata columns (optional)
- additional metadata Json Column name (optional, default: "langchain4j_metadata")
- overwrite existing enabled(optional, default: false)
- store metadata enabled (optional, default: false)

example usage:
```java
...
import dev.langchain4j.engine.PostgresEngine;
import dev.langchain4j.engine.EmbeddingStoreConfig;
import dev.langchain4j.store.embedding.cloudsql.MetadataColumn;
import java.util.ArrayList;
...
        List<MetadataColumn> metadataColumns = new ArrayList<>();
        metadataColumns.add(new MetadataColumn("page", "TEXT", true));
        metadataColumns.add(new MetadataColumn("source", "TEXT", false));
        EmbeddingStoreConfig customParams = new EmbeddingStoreConfig.Builder("MY_TABLE_NAME", 768)
                .contentColumn("my_content_column")
                .embeddingColumn("my_embedding_column")
                .metadataColumns(metadataColumns)
                .metadataJsonColumn("my_metadata_json_column")
                .storeMetadata(true)
                .build();
        engine.initVectorStoreTable(customParams);
```

## PostgresEmbeddingStore Usage

Use a vector store to store text embedded data and perform vector search, instances of `PostgresEmbeddingStore` can be created by configuring provided `Builder`, it requires the following:
- `PostgresEngine` instance
- table name
- schema name (optional, default: "public")
- content column (optional, default: "content")
- embedding column (optional, default: "embedding")
- id column (optional, default: "langchain4j_id")
- metadata column names (optional)
- additional metadata json column (optional, default: "langchain4j_metadata")
- ignored metadata column names (optional)
- distance strategy (optional, default:DistanceStrategy.COSINE_DISTANCE)
- query options (optional).

example usage:
```java
...
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.engine.PostgresEngine;
import dev.langchain4j.engine.EmbeddingStoreConfig;
import dev.langchain4j.store.embedding.cloudsql.MetadataColumn;
import dev.langchain4j.store.embedding.cloudsql.PostgresEmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import java.util.ArrayList;
...

    PostgresEmbeddingStore store = new PostgresEmbeddingStore.Builder(engine, TABLE_NAME)
        .build();

    List<String> testTexts = Arrays.asList("cat", "dog", "car", "truck");
    List<Embedding> embeddings = new ArrayList<>();
    List<TextSegment> textSegments = new ArrayList<>();

    for (String text : testTexts) {
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put("string_metadata", "sring");
        Metadata metadata = new Metadata(metaMap);
        textSegments.add(new TextSegment(text, metadata));
        embeddings.add(MyEmbeddingModel.embed(text).content());
    }
    List<String> ids = store.addAll(embeddings, textSegments);
    // search for "cat"
    EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
            .queryEmbedding(embeddings.get(0))
            .maxResults(10)
            .minScore(0.9)
            .build();
    List<EmbeddingMatch<TextSegment>> result = store.search(request).matches();
    // remove cat
    store.removeAll(singletonList(result.get(0).embeddingId()));

```

