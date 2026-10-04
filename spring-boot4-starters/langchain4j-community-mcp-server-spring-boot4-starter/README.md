# Spring Boot 4 STDIO MCP server

This opt-in starter exposes explicitly selected Spring beans using the existing
`langchain4j-community-mcp-server` and `StdioMcpServerTransport`. It requires Java 17
or later and Spring Boot 4. There are no Spring AI dependencies.

Add `dev.langchain4j:langchain4j-community-mcp-server-spring-boot4-starter`, with its
version managed by the LangChain4j Community BOM. The `boot4` suffix follows the
repository convention: unsuffixed starters target Boot 3. This module does not
provide a Boot 3 variant.

## Configuration

```properties
spring.main.web-application-type=none
spring.main.banner-mode=off
logging.config=classpath:logback-mcp.xml
langchain4j.community.mcp.server.enabled=true
langchain4j.community.mcp.server.tool-bean-names=calculator
```

```java
@Configuration(proxyBeanMethods = false)
class ToolConfiguration {
    @Bean
    Calculator calculator() {
        return new Calculator();
    }

    static class Calculator {
        @Tool("Add two integers")
        public int add(int a, int b) {
            return a + b;
        }
    }
}
```

The imports above are `org.springframework.context.annotation.Bean`,
`org.springframework.context.annotation.Configuration`, and
`dev.langchain4j.agent.tool.Tool`.

**Stdout is exclusively for MCP JSON-RPC.** Disable the banner and direct all
console logging to stderr before starting the application. With Boot's default
Logback dependency, place the following `logback-mcp.xml` in `src/main/resources`
and select it with `logging.config` as above:

```xml
<configuration>
    <appender name="STDERR" class="ch.qos.logback.core.ConsoleAppender">
        <target>System.err</target>
        <encoder>
            <pattern>%level %logger - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDERR"/>
    </root>
</configuration>
```

Custom logging configurations and alternative logging systems must also use stderr. Tool
implementations, libraries, startup hooks, and shutdown hooks must not print to
stdout. The starter deliberately does not rewrite global logging or banner settings.
Compile tools with `-parameters`, or use `@P(name = "...")`, to retain meaningful
argument names in their JSON schemas.

Activation is disabled by default. When enabled, `tool-bean-names` must be a
nonempty list of distinct singleton bean names. Missing names, blank names,
duplicate selections (including aliases), and beans without declared `@Tool`
methods fail startup. Duplicate protocol tool names are rejected by the core
server. No classpath scanning or automatic discovery of other tool beans occurs.
Only methods declared on the selected bean's concrete class are supported by the
current server; inherited-only tool definitions are not supported.

Spring AOP proxies (JDK and CGLIB) and other JDK proxies are rejected explicitly.
The current core server's reflection-based discovery cannot reliably register
their methods. Do not unwrap a secured or transactional proxy. Instead select an
unproxied `@Tool` facade which injects and calls the proxied service, preserving
its interceptors. The starter never invokes an underlying proxy target directly.

## Lifecycle and customization

Use this starter for a dedicated, client-launched STDIO application. The transport
is lazily constructed during Spring's lifecycle startup, once per application
context, on `ApplicationReadyEvent` after startup runners finish. Deferring until
readiness avoids racing application startup against an immediate EOF. A non-daemon
waiter keeps a non-web process alive after `main` returns.
EOF on stdin (or closing the transport) closes the application context, including
its managed resources. Closing the application also closes the transport and
releases the waiter. The session is not restartable. No `System.exit` is used:
application-created non-daemon threads must also be stopped by their owners.
EOF terminates the session; clients should await outstanding responses before
closing stdin.

A user-defined `McpServer` replaces default tool registration (and owns validation
of its tools). A user-defined `StdioMcpServerTransport` replaces the default
transport and is managed by the lifecycle; use `@Lazy` and
`@Bean(destroyMethod = "")` to defer its constructor-started threads and avoid
duplicate close calls. A user-defined `McpServerLifecycle` replaces lifecycle
management entirely. Custom transports must implement `awaitClose()` and
`close()` consistently. Keep auto-configuration disabled if managing the whole
server yourself.

This starter does not add HTTP, OAuth, model configuration, or agent orchestration.
