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
3. **Broad catalog:**
   50+ integration modules spanning model providers, embedding stores, chat memory stores, content retrievers,
   web search engines, document loaders/parsers/transformers, code & browser execution engines, agent tools,
   MCP, and more.

Browse the available integrations under [Integrations](https://docs.langchain4j.dev/category/integrations)
in the documentation, or explore the module directories in this repository.

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
  - [Spring Boot 3 STDIO MCP server](spring-boot-starters/langchain4j-community-mcp-server-spring-boot-starter/README.md)
  - [Spring Boot 4 STDIO MCP server](spring-boot4-starters/langchain4j-community-mcp-server-spring-boot4-starter/README.md)
  - [Prompt repetition (non-RAG + RAG)](langchain4j-community-prompt-repetition/README.md)
  - [SQL chat memory store](chat-memory-stores/langchain4j-community-sql/README.md)
  - [MongoDB chat memory store](chat-memory-stores/langchain4j-community-mongodb/README.md)
  - [Lucene content retriever](content-retrievers/langchain4j-community-lucene/README.md)
  - [Exa document loader](document-loaders/langchain4j-community-document-loader-exa/README.md)

## Getting Started

Pick an integration from the [documentation](https://docs.langchain4j.dev/category/integrations) and add its Maven dependency.
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

## Code Examples

- [langchain4j-examples](https://github.com/langchain4j/langchain4j-examples) — the official examples repository,
  which also covers several community integrations.
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
