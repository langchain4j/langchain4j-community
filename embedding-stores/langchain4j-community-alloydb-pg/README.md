# Google AlloyDB for PostgreSQL

The **AlloyDB for PostgreSQL for LangChain4J** package provides a first class experience for connecting to
AlloyDB instances from the LangChain4j ecosystem while providing the following benefits:

- **Simplified & Secure Connections**: easily and securely create shared connection pools to connect to Google Cloud databases utilizing IAM for authorization and database authentication without needing to manage SSL certificates, configure firewall rules, or enable authorized networks.
- **Improved performance & Simplified management**: use a single-table schema can lead to faster query execution, especially for large collections.
- **Improved metadata handling**: store metadata in columns instead of JSON, resulting in significant performance improvements.
- **Clear separation**: clearly separate table and extension creation, allowing for distinct permissions and streamlined workflows.
- **Better integration with AlloyDB**: built-in methods to take advantage of AlloyDB's advanced indexing and scalability capabilities.

Learn more about [AlloyDB for PostgreSQL](https://cloud.google.com/alloydb).

## Before you begin

In order to use this library, you first need to go through the following
steps:

1. [Select or create a Cloud Platform project.](https://console.cloud.google.com/project)
2. [Enable billing for your project.](https://cloud.google.com/billing/docs/how-to/modify-project#enable_billing_for_a_project)
3. [Enable the AlloyDB API.](https://console.cloud.google.com/flows/enableapi?apiid=alloydb.googleapis.com)
4. [Setup Authentication.](https://googleapis.dev/python/google-api-core/latest/auth.html)
5. [Create a database.](https://cloud.google.com/alloydb/docs/quickstart/create-and-connect)

### Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artificatId>langchain4j-community-alloydb-pg</artificatId>
    <version>1.1.0-beta7-SNAPSHOT</version>
</dependency>
```

## AlloyDBEngine Usage

Instances of `AlloyDBEngine` can be created by configuring provided `Builder`. The `AlloyDBEngine` configures a connection pool to your AlloyDB database,
enabling successful connections from your application and following industry best practices.
Securely connect to AlloyDB by using the built-in [AlloyDB Java Connector](https://github.com/GoogleCloudPlatform/alloydb-java-connector/tree/main) using Builder
options:
 - project id
 - region
 - cluster
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

Connect to an AlloyDB Omni instance by specifying the host:
 - host
 - port (optional, default: 5432).
 - database
 - user
 - password

```java
import dev.langchain4j.community.store.embedding.alloydb.AlloyDBEngine;

AlloyDBEngine engine = new AlloyDBEngine.Builder()
        .projectId("my-projectId")
        .region("my-region")
        .cluster("my-cluster")
        .instance("my-instance")
        .database("my-database")
        .build();

```


### Initialize a Vector Store table
`AlloyDBEngine` provides `initVectorStore` method to initialize the vector store table, it requires a `EmbeddingStoreConfig` object that can be configured with its provided `Builder` it requires the following:
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
import dev.langchain4j.community.store.embedding.alloydb.EmbeddingStoreConfig;
import dev.langchain4j.community.store.embedding.alloydb.MetadataColumn;
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

## AlloyDBEmbeddingStore Usage

Use a vector store to store text embedded data and perform vector search, instances of `AlloyDBEmbeddingStore` can be created by configuring provided `Builder`, it requires the following:
- `AlloyDBEngine` instance
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
import dev.langchain4j.community.store.embedding.alloydb.AlloyDBEmbeddingStore;
...

AlloyDBEmbeddingStore store = new AlloyDBEmbeddingStore.Builder(engine, TABLE_NAME)
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

## Document Loader Usage

Use a document loader to load data as LangChain4j `Document`.

```java

import dev.langchain4j.data.document.Document;
import dev.langchain4j.community.data.document.loader.alloydb.AlloyDBLoader;


   AlloyDBLoader loader = AlloyDBLoader.builder()
                .engine(engine)
                .tableName("my-table-name")
                .build();
   List<Document> docs = loader.load();

   ```
