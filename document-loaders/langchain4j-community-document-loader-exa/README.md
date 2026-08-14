# LangChain4j Exa Document Loader

This module provides integration with [Exa.ai](https://exa.ai/) for loading web content as documents in LangChain4j.

## Overview

Exa.ai is a semantic search API that provides high-quality, relevant results from across the web.  
The `ExaDocumentLoader` allows you to search and retrieve content via Exa's API and convert the results into LangChain4j `Document` objects, including structured metadata.

## Features

- Search the web using Exa.ai's semantic search API
- Retrieve full text content from search results (optional)
- Automatic metadata extraction:
  - Title
  - URL
  - Author
  - Published date
  - Relevance score
- Configurable number of search results
- Support for different search types via `ExaSearchType`
- Graceful fallback to highlights or title if full text is unavailable
- Rich metadata suitable for downstream processing

## Installation

Add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-document-loader-exa</artifactId>
</dependency>
```

## Usage

### Basic Usage

```java
import dev.langchain4j.community.data.document.loader.exa.ExaDocumentLoader;
import dev.langchain4j.data.document.Document;
import java.util.List;

ExaDocumentLoader loader = ExaDocumentLoader.builder()
    .apiKey("your-exa-api-key")
    .numResults(5)
    .build();

List<Document> documents = loader.loadDocuments("artificial intelligence trends");

for (Document document : documents) {
    System.out.println("Title: " + document.metadata().getString("title"));
    System.out.println("URL: " + document.metadata().getString("url"));
    System.out.println("Author: " + document.metadata().getString("author"));
    System.out.println("Published Date: " + document.metadata().getString("published_date"));
    System.out.println("Score: " + document.metadata().getDouble("score"));
    System.out.println("Content: " + document.text());
    System.out.println("---");
}
```

### Advanced Configuration

```java
import dev.langchain4j.community.data.document.loader.exa.ExaDocumentLoader;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

HttpClient customHttpClient = new JdkHttpClientBuilder().build();
ObjectMapper customMapper = new ObjectMapper();

ExaDocumentLoader loader = ExaDocumentLoader.builder()
    .apiKey("your-exa-api-key")
    .numResults(5)
    .searchType(ExaSearchType.AUTO)
    .includeText(true)
    .httpClient(customHttpClient)
    .objectMapper(customMapper)
    .build();

List<Document> documents = loader.loadDocuments("latest machine learning papers");
```

## Metadata

**Field Descriptions:**

- **title**: The title of the web page
- **url**: The URL of the web page
- **author**: Author of the content
- **published_date**: Publication date
- **score**: Relevance score from Exa

## Error Handling

All API and parsing errors are wrapped in `ExaDocumentLoaderException` to provide a consistent exception model.

## API Key

You need an Exa.ai API key to use this loader. You can obtain one by signing up at [https://exa.ai/](https://exa.ai/).

