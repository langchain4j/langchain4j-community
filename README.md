# LangChain4j Community

[![Build Status](https://img.shields.io/github/actions/workflow/status/langchain4j/langchain4j-community/main.yaml?branch=main&style=for-the-badge&label=CI%20BUILD&logo=github)](https://github.com/langchain4j/langchain4j-community/actions/workflows/main.yaml)
[![Nightly Build](https://img.shields.io/github/actions/workflow/status/langchain4j/langchain4j-community/nightly_jdk17.yaml?branch=main&style=for-the-badge&label=NIGHTLY%20BUILD&logo=github)](https://github.com/langchain4j/langchain4j-community/actions/workflows/nightly_jdk17.yaml)
[![Discord](https://img.shields.io/discord/1156626270772269217?logo=discord&style=for-the-badge)](https://discord.gg/JzTFvyjG6R)
[![Maven Version](https://img.shields.io/maven-central/v/dev.langchain4j/langchain4j-community-bom?logo=apachemaven&style=for-the-badge)](https://central.sonatype.com/artifact/dev.langchain4j/langchain4j-community-bom)
[![License](https://img.shields.io/github/license/langchain4j/langchain4j-community?style=for-the-badge)](https://github.com/langchain4j/langchain4j-community/blob/main/LICENSE)

Community-driven integrations for [LangChain4j](https://github.com/langchain4j/langchain4j): model providers, embedding stores, chat memory stores, tools, web search engines, document ingestion, execution engines, MCP, and more — all built on the same unified LangChain4j APIs.

## Introduction

Welcome!

LangChain4j Community is the companion repository of [LangChain4j](https://github.com/langchain4j/langchain4j),
hosting integrations that are built and maintained by the community.

Here's how it relates to the main repository:

1. **Same unified APIs:**
   Every module here implements the standard LangChain4j interfaces (`ChatModel`, `EmbeddingModel`, `EmbeddingStore`,
   `ChatMemoryStore`, `ContentRetriever`, `WebSearchEngine`, ...), so community integrations are used exactly like
   core ones and can be swapped in and out without changing your application code.
2. **Faster iteration:**
   New integrations land here first, at a release cadence independent of the core repository.
   Mature integrations may graduate into the main [langchain4j](https://github.com/langchain4j/langchain4j) repository over time.
3. **Broad catalog:**
   50+ integration modules across model providers (DashScope/Qwen, Zhipu AI, Qianfan, Cohere, OCI GenAI, Xinference, ...),
   embedding stores (Redis, Neo4j, ClickHouse, DuckDB, SQL Server, OceanBase, Amazon S3 Vectors, ...),
   chat memory stores, content retrievers, web search engines, document loaders/parsers/transformers,
   code & browser execution engines, agent tools, and an MCP server.

The full catalog is listed in the [Integrations](#integrations) section below.

### Versioning & compatibility

Community artifacts are versioned as `<langchain4j-version>-beta<N>`.
For example, community version `1.21.0-beta31` is built against LangChain4j `1.21.0`.
Choose the community version whose prefix matches the LangChain4j version used in your project.

| LangChain4j Community | LangChain4j | Java |
|-----------------------|-------------|------|
| `1.21.0-beta31` (latest) | `1.21.0` | 17+ |

Community modules are released with a `-beta<N>` suffix: APIs are generally stable, but occasional
breaking changes may happen between releases. Pin your dependency versions and review the
[release notes](https://github.com/langchain4j/langchain4j-community/releases) when upgrading.

## Documentation

- [LangChain4j documentation](https://docs.langchain4j.dev) — the official docs; community integrations appear
  alongside core ones under [Integrations](https://docs.langchain4j.dev/category/integrations).
- [Javadoc](https://docs.langchain4j.dev/apidocs/index.html)
- Module guides in this repository:
  - [Prompt repetition (non-RAG + RAG)](langchain4j-community-prompt-repetition/README.md)
  - [SQL chat memory store](chat-memory-stores/langchain4j-community-sql/README.md)
  - [MongoDB chat memory store](chat-memory-stores/langchain4j-community-mongodb/README.md)
  - [Lucene content retriever](content-retrievers/langchain4j-community-lucene/README.md)
  - [Exa document loader](document-loaders/langchain4j-community-document-loader-exa/README.md)

## Getting Started

Pick an integration from the [catalog](#integrations) and add its Maven dependency.
Below is a quick start with [DashScope (Qwen)](https://docs.langchain4j.dev/integrations/language-models/dashscope)
as an example — every other module works the same way.

### Plain Java

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-dashscope</artifactId>
    <version>1.21.0-beta31</version>
</dependency>
```

You can also use the BOM to keep all community module versions consistent:

```xml
<dependencyManagement>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-community-bom</artifactId>
        <version>1.21.0-beta31</version>
        <type>pom</type>
        <scope>import</scope>
    </dependency>
</dependencyManagement>
```

```java
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.model.chat.ChatModel;

ChatModel model = QwenChatModel.builder()
        .apiKey(System.getenv("DASHSCOPE_API_KEY"))
        .modelName("qwen-plus")
        .build();

String answer = model.chat("Hello from LangChain4j Community!");
System.out.println(answer);
```

### Spring Boot

Use the matching Spring Boot starter (see [spring-boot-starters](spring-boot-starters) for Spring Boot 3.x
and [spring-boot4-starters](spring-boot4-starters) for Spring Boot 4.x):

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-community-dashscope-spring-boot-starter</artifactId>
    <version>1.21.0-beta31</version>
</dependency>
```

```properties
langchain4j.community.dashscope.chat-model.api-key=${DASHSCOPE_API_KEY}
langchain4j.community.dashscope.chat-model.model-name=qwen-plus
```

The starter auto-configures `ChatModel`, `StreamingChatModel`, `EmbeddingModel`, and other beans,
which you can then inject wherever needed.

Spring Boot starters are currently available for:
ClickHouse, CockroachDB, Cohere, DashScope, Neo4j, OceanBase, Qianfan, Redis, S3 Vectors, Valkey, Vearch, Xinference, and Zhipu AI.

## Integrations

Module names link to the source in this repository; documentation links (where available) lead to
[docs.langchain4j.dev](https://docs.langchain4j.dev).

### Model providers

| Module | Provider | Docs |
|--------|----------|------|
| [`langchain4j-community-dashscope`](models/langchain4j-community-dashscope) | Alibaba Cloud DashScope — Qwen chat/embedding/scoring models, Wanx image generation, tokenizer | [Docs](https://docs.langchain4j.dev/integrations/language-models/dashscope) |
| [`langchain4j-community-zhipu-ai`](models/langchain4j-community-zhipu-ai) | Zhipu AI (GLM) chat & embedding models | [Docs](https://docs.langchain4j.dev/integrations/language-models/zhipu-ai) |
| [`langchain4j-community-chatglm`](models/langchain4j-community-chatglm) | ChatGLM chat model | [Docs](https://docs.langchain4j.dev/integrations/language-models/chatglm) |
| [`langchain4j-community-qianfan`](models/langchain4j-community-qianfan) | Baidu Qianfan (ERNIE) chat & embedding models | [Docs](https://docs.langchain4j.dev/integrations/language-models/qianfan) |
| [`langchain4j-community-cohere`](models/langchain4j-community-cohere) | Cohere chat, embedding & reranking (scoring) models | [Docs](https://docs.langchain4j.dev/integrations/language-models/cohere) |
| [`langchain4j-community-oci-genai`](models/langchain4j-community-oci-genai) | Oracle Cloud Infrastructure GenAI chat & embedding models | [Docs](https://docs.langchain4j.dev/integrations/language-models/oci-genai) |
| [`langchain4j-community-xinference`](models/langchain4j-community-xinference) | Xinference — self-hosted model serving (chat, embedding, reranking, image) | [Docs](https://docs.langchain4j.dev/integrations/language-models/xinference) |
| [`langchain4j-community-twelvelabs`](models/langchain4j-community-twelvelabs) | TwelveLabs video understanding & multimodal embeddings | — |
| [`langchain4j-community-responsible-ai`](models/langchain4j-community-responsible-ai) | Responsible AI Labs guardrails — content moderation & prompt injection detection | — |
| [`langchain4j-community-model-router`](models/langchain4j-community-model-router) | Route requests across multiple chat models via configurable strategies | — |
| [`langchain4j-community-model-registry`](models/langchain4j-community-model-registry) | Registry of model metadata: providers, modalities, context limits, cost | — |

### Embedding stores

| Module | Store | Docs |
|--------|-------|------|
| [`langchain4j-community-redis`](embedding-stores/langchain4j-community-redis) | Redis | [Docs](https://docs.langchain4j.dev/integrations/embedding-stores/redis) |
| [`langchain4j-community-valkey`](embedding-stores/langchain4j-community-valkey) | Valkey | [Docs](https://docs.langchain4j.dev/integrations/embedding-stores/valkey) |
| [`langchain4j-community-neo4j`](embedding-stores/langchain4j-community-neo4j) | Neo4j | [Docs](https://docs.langchain4j.dev/integrations/embedding-stores/neo4j) |
| [`langchain4j-community-clickhouse`](embedding-stores/langchain4j-community-clickhouse) | ClickHouse | [Docs](https://docs.langchain4j.dev/integrations/embedding-stores/clickhouse) |
| [`langchain4j-community-duckdb`](embedding-stores/langchain4j-community-duckdb) | DuckDB | [Docs](https://docs.langchain4j.dev/integrations/embedding-stores/duckdb) |
| [`langchain4j-community-sqlserver`](embedding-stores/langchain4j-community-sqlserver) | Microsoft SQL Server | [Docs](https://docs.langchain4j.dev/integrations/embedding-stores/sqlserver) |
| [`langchain4j-community-cockroachdb`](embedding-stores/langchain4j-community-cockroachdb) | CockroachDB | [Docs](https://docs.langchain4j.dev/integrations/embedding-stores/cockroachdb) |
| [`langchain4j-community-yugabytedb`](embedding-stores/langchain4j-community-yugabytedb) | YugabyteDB | [Docs](https://docs.langchain4j.dev/integrations/embedding-stores/yugabytedb) |
| [`langchain4j-community-oceanbase`](embedding-stores/langchain4j-community-oceanbase) | OceanBase | [Docs](https://docs.langchain4j.dev/integrations/embedding-stores/oceanbase) |
| [`langchain4j-community-alloydb-pg`](embedding-stores/langchain4j-community-alloydb-pg) | Google AlloyDB for PostgreSQL | [Docs](https://docs.langchain4j.dev/integrations/embedding-stores/alloydb) |
| [`langchain4j-community-cloud-sql-pg`](embedding-stores/langchain4j-community-cloud-sql-pg) | Google Cloud SQL for PostgreSQL | [Docs](https://docs.langchain4j.dev/integrations/embedding-stores/cloud-sql) |
| [`langchain4j-community-s3-vectors`](embedding-stores/langchain4j-community-s3-vectors) | Amazon S3 Vectors | [Docs](https://docs.langchain4j.dev/integrations/embedding-stores/amazon-s3-vectors) |
| [`langchain4j-community-arcadedb`](embedding-stores/langchain4j-community-arcadedb) | ArcadeDB | [Docs](https://docs.langchain4j.dev/integrations/embedding-stores/arcadedb) |
| [`langchain4j-community-vearch`](embedding-stores/langchain4j-community-vearch) | Vearch | [Docs](https://docs.langchain4j.dev/integrations/embedding-stores/vearch) |
| [`langchain4j-community-hazelcast-enterprise`](embedding-stores/langchain4j-community-hazelcast-enterprise) | Hazelcast (Enterprise) | [Docs](https://docs.langchain4j.dev/integrations/embedding-stores/hazelcast) |
| [`langchain4j-community-jvector`](embedding-stores/langchain4j-community-jvector) | JVector — in-process vector store | [Docs](https://docs.langchain4j.dev/integrations/embedding-stores/jvector) |
| [`langchain4j-community-dynamodb`](embedding-stores/langchain4j-community-dynamodb) | Amazon DynamoDB | — |
| [`langchain4j-community-lancedb`](embedding-stores/langchain4j-community-lancedb) | LanceDB | — |
| [`langchain4j-community-memfile`](embedding-stores/langchain4j-community-memfile) | Memfile | — |

### Chat memory stores

| Module | Store |
|--------|-------|
| [`langchain4j-community-sql`](chat-memory-stores/langchain4j-community-sql) | SQL databases via JDBC ([README](chat-memory-stores/langchain4j-community-sql/README.md)) |
| [`langchain4j-community-mongodb`](chat-memory-stores/langchain4j-community-mongodb) | MongoDB ([README](chat-memory-stores/langchain4j-community-mongodb/README.md)) |
| [`langchain4j-community-hazelcast`](chat-memory-stores/langchain4j-community-hazelcast) | Hazelcast |

### Content retrievers

| Module | Retriever |
|--------|-----------|
| [`langchain4j-community-lucene`](content-retrievers/langchain4j-community-lucene) | Apache Lucene ([README](content-retrievers/langchain4j-community-lucene/README.md)) |
| [`langchain4j-community-neo4j-retriever`](content-retrievers/langchain4j-community-neo4j-retriever) | Neo4j Text2Cypher retriever — query knowledge graphs with natural language |

### Web search engines

| Module | Engine | Docs |
|--------|--------|------|
| [`langchain4j-community-web-search-engine-searxng`](web-search-engines/langchain4j-community-web-search-engine-searxng) | SearXNG | [Docs](https://docs.langchain4j.dev/integrations/web-search-engines/searxng) |
| [`langchain4j-community-web-search-engine-duckduckgo`](web-search-engines/langchain4j-community-web-search-engine-duckduckgo) | DuckDuckGo | [Docs](https://docs.langchain4j.dev/integrations/web-search-engines/duckduckgo) |
| [`langchain4j-community-web-search-engine-brave`](web-search-engines/langchain4j-community-web-search-engine-brave) | Brave Search | — |
| [`langchain4j-community-web-search-engine-serply`](web-search-engines/langchain4j-community-web-search-engine-serply) | Serply | — |
| [`langchain4j-community-web-search-engine-firecrawl`](web-search-engines/langchain4j-community-web-search-engine-firecrawl) | Firecrawl | — |

### Document ingestion

| Module | Category | Description |
|--------|----------|-------------|
| [`langchain4j-community-document-loader-confluence`](document-loaders/langchain4j-community-document-loader-confluence) | Loader | Load documents from Atlassian Confluence |
| [`langchain4j-community-document-loader-exa`](document-loaders/langchain4j-community-document-loader-exa) | Loader | Load documents via Exa search ([README](document-loaders/langchain4j-community-document-loader-exa/README.md)) |
| [`langchain4j-community-document-loader-gitlab`](document-loaders/langchain4j-community-document-loader-gitlab) | Loader | Load documents from GitLab |
| [`langchain4j-community-document-parser-llamaparse`](document-parsers/langchain4j-community-document-parser-llamaparse) | Parser | Parse documents with LlamaParse |
| [`langchain4j-community-llm-graph-transformer`](document-transformers/langchain4j-community-llm-graph-transformer) | Transformer | Transform documents into knowledge graphs with an LLM |

### Code & browser execution engines

| Module | Category | Docs |
|--------|----------|------|
| [`langchain4j-community-code-execution-engine-local`](code-execution-engines/langchain4j-community-code-execution-engine-local) | Code execution — local JVM process | [Docs](https://docs.langchain4j.dev/integrations/code-execution-engines/local) |
| [`langchain4j-community-code-execution-engine-docker`](code-execution-engines/langchain4j-community-code-execution-engine-docker) | Code execution — Docker containers | — |
| [`langchain4j-community-browser-execution-engine-playwright`](browser-execution-engines/langchain4j-community-browser-execution-engine-playwright) | Browser automation with Playwright | [Docs](https://docs.langchain4j.dev/integrations/browser-execution-engines/playwright) |

### Tools

| Module | Description |
|--------|-------------|
| [`langchain4j-community-tool-jira`](tools/langchain4j-community-tool-jira) | Query and manage Jira issues |
| [`langchain4j-community-tool-browser-use`](tools/langchain4j-community-tool-browser-use) | Execute actions in a browser |
| [`langchain4j-community-tool-web-scraper`](tools/langchain4j-community-tool-web-scraper) | Scrape web pages and convert HTML to Markdown |
| [`langchain4j-community-tool-xquik`](tools/langchain4j-community-tool-xquik) | Read-only X/Twitter research tools (via the Xquik API) |
| [`langchain4j-community-tool-live-tennis`](tools/langchain4j-community-tool-live-tennis) | Live tennis scores, fixtures, rankings & head-to-head |
| [`langchain4j-community-tool-salt`](tools/langchain4j-community-tool-salt) | Chat on Salt — end-to-end encrypted chat where humans and AI agents are equal contacts |

### MCP & prompt techniques

| Module | Description | Docs |
|--------|-------------|------|
| [`langchain4j-community-mcp-server`](mcp/langchain4j-community-mcp-server) | Expose your own tools and services as an MCP (Model Context Protocol) server | — |
| [`langchain4j-community-prompt-repetition`](langchain4j-community-prompt-repetition) | Prompt repetition — repeats the user query to improve model attention/adherence to the question, for both non-RAG (input guardrail) and RAG (query transformer) usage ([README](langchain4j-community-prompt-repetition/README.md)) | [Docs](https://docs.langchain4j.dev/integrations/prompt-repetition/) |

## Code Examples

- [langchain4j-examples](https://github.com/langchain4j/langchain4j-examples) — the official examples repository,
  including community integrations:
  [Neo4j](https://github.com/langchain4j/langchain4j-examples/tree/main/neo4j-example),
  [Redis](https://github.com/langchain4j/langchain4j-examples/tree/main/redis-example),
  [JVector](https://github.com/langchain4j/langchain4j-examples/tree/main/jvector-example),
  [AlloyDB](https://github.com/langchain4j/langchain4j-examples/tree/main/google-alloydb-example),
  [S3 Vectors](https://github.com/langchain4j/langchain4j-examples/tree/main/s3-vectors-example),
  [YugabyteDB](https://github.com/langchain4j/langchain4j-examples/tree/main/yugabytedb-example),
  and [Spring Boot](https://github.com/langchain4j/langchain4j-examples/tree/main/spring-boot-example) /
  [Spring Boot 4](https://github.com/langchain4j/langchain4j-examples/tree/main/spring-boot4-example).
- Every module ships integration tests under `src/test` that double as runnable usage examples,
  e.g. [DashScope tests](models/langchain4j-community-dashscope/src/test).

## Get Help

Please use [Discord](https://discord.gg/JzTFvyjG6R) or [Stack Overflow](https://stackoverflow.com/search?q=langchain4j-community) to get help.

## Request Features

Please let us know what features you need by [opening an issue](https://github.com/langchain4j/langchain4j-community/issues/new/choose).

## Contribute

Contribution guidelines can be found [here](https://github.com/langchain4j/langchain4j-community/blob/main/CONTRIBUTING.md).

We welcome contributions of all kinds: new integrations, bug fixes, documentation, and examples.
A good way to start is to look at the open issues labeled
[P1](https://github.com/langchain4j/langchain4j-community/issues?q=is%3Aissue+is%3Aopen+label%3AP1) and
[P2](https://github.com/langchain4j/langchain4j-community/issues?q=is%3Aissue+is%3Aopen+label%3AP2).

Before submitting a PR, run `make lint` and `make format` locally.

## License

This project is licensed under the [Apache License 2.0](https://github.com/langchain4j/langchain4j-community/blob/main/LICENSE).
