# SQL Chat Memory Store

This module provides a SQL-backed implementation of `ChatMemoryStore` for persisting chat history.

## Maven Dependency

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-sql</artifactId>
    <version>1.14.0-beta24-SNAPSHOT</version>
</dependency>
```

## Usage

```java
import dev.langchain4j.community.store.memory.chat.sql.MySQLDialect;

ChatMemoryStore store = SQLChatMemoryStore.builder()
        .dataSource(dataSource)
        .sqlDialect(new MySQLDialect())
        .tableName("chat_memory_test")
        .autoCreateTable(true)
        .memoryIdColumnName("memory_id")
        .contentColumnName("content")
        .build();
```
