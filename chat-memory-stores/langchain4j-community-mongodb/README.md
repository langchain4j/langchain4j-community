# MongoDB Chat Memory Store

This module provides a MongoDB-backed implementation of `ChatMemoryStore` for persisting chat history.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-mongodb</artifactId>
    <version>1.13.0-beta23</version>
</dependency>
```

## Usage

```java
ChatMemoryStore store = MongoDbChatMemoryStore.builder()
    .mongoClient(mongoClient)
    .databaseName("my_db")
    .collectionName("chat_history")
    .expireAfterSeconds(3600L) // optional TTL
    .build();
```
