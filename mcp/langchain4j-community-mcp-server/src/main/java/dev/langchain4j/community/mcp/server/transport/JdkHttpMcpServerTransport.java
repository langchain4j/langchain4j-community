package dev.langchain4j.community.mcp.server.transport;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureBetween;
import static dev.langchain4j.internal.ValidationUtils.ensureGreaterThanZero;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.community.mcp.server.McpServer;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serves an {@link McpServer} over the stateless Streamable HTTP transport (see {@link McpHttpHandler}) using the
 * HTTP server built into the JDK ({@code com.sun.net.httpserver}), so no additional dependency is needed.
 *
 * <pre>{@code
 * McpServer server = new McpServer(List.of(new MyTools()));
 * try (JdkHttpMcpServerTransport transport = JdkHttpMcpServerTransport.builder()
 *         .server(server)
 *         .port(8080)
 *         .build()) {
 *     transport.awaitClose();
 * }
 * }</pre>
 *
 * The server binds to {@code 127.0.0.1} by default, as the specification recommends for local servers; set
 * {@link Builder#host(String)} to {@code 0.0.0.0} to accept remote connections. Applications that already run an
 * HTTP server (e.g. a servlet container) should mount {@link McpHttpHandler} on it instead.
 */
public class JdkHttpMcpServerTransport implements Closeable {

    private static final int DEFAULT_MAX_THREADS = 64;
    private static final int DEFAULT_MAX_REQUEST_BODY_SIZE = 4 * 1024 * 1024;

    private final McpHttpHandler handler;
    private final String path;
    private final int maxRequestBodySize;
    private final HttpServer httpServer;
    private final ExecutorService executor;
    private final Object closeLock = new Object();
    private boolean closed;

    private JdkHttpMcpServerTransport(Builder builder) {
        this.handler = new McpHttpHandler(
                ensureNotNull(builder.server, "server"), getOrDefault(builder.allowedOrigins, List.of()));
        this.path = ensureNotBlank(getOrDefault(builder.path, "/mcp"), "path");
        this.maxRequestBodySize = ensureGreaterThanZero(
                getOrDefault(builder.maxRequestBodySize, DEFAULT_MAX_REQUEST_BODY_SIZE), "maxRequestBodySize");
        int port = ensureBetween(getOrDefault(builder.port, 8080), 0, 65535, "port");
        String host = getOrDefault(builder.host, "127.0.0.1");
        this.executor =
                newExecutor(ensureGreaterThanZero(getOrDefault(builder.maxThreads, DEFAULT_MAX_THREADS), "maxThreads"));
        try {
            this.httpServer = HttpServer.create(new InetSocketAddress(host, port), 0);
        } catch (IOException e) {
            executor.shutdownNow();
            throw new UncheckedIOException(e);
        }
        httpServer.createContext(path, this::exchange);
        httpServer.setExecutor(executor);
        httpServer.start();
    }

    /**
     * @return the port the server listens on; useful when it was started on port {@code 0}
     */
    public int port() {
        return httpServer.getAddress().getPort();
    }

    /**
     * @return the URL of the MCP endpoint, e.g. {@code http://127.0.0.1:8080/mcp}
     */
    public URI endpoint() {
        InetSocketAddress address = httpServer.getAddress();
        return URI.create("http://" + address.getHostString() + ":" + address.getPort() + path);
    }

    /**
     * Blocks until this transport is closed.
     */
    public void awaitClose() throws InterruptedException {
        synchronized (closeLock) {
            while (!closed) {
                closeLock.wait();
            }
        }
    }

    @Override
    public void close() {
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            closed = true;
            closeLock.notifyAll();
        }
        httpServer.stop(0);
        executor.shutdownNow();
    }

    private void exchange(HttpExchange exchange) throws IOException {
        // HttpExchange is AutoCloseable only since Java 18, this module targets Java 17
        try {
            send(exchange, respond(exchange));
        } finally {
            exchange.close();
        }
    }

    private McpHttpHandler.Response respond(HttpExchange exchange) throws IOException {
        if (!path.equals(exchange.getRequestURI().getPath())) {
            return McpHttpHandler.Response.empty(404);
        }
        String body = readBody(exchange.getRequestBody());
        if (body == null) {
            return McpHttpHandler.Response.empty(413);
        }
        try {
            return handler.handle(exchange.getRequestMethod(), exchange.getRequestHeaders()::getFirst, body);
        } catch (RuntimeException e) {
            return McpHttpHandler.Response.empty(500);
        }
    }

    /**
     * @return the body, or {@code null} if it is larger than {@link #maxRequestBodySize}
     */
    private String readBody(InputStream input) throws IOException {
        byte[] bytes = input.readNBytes(maxRequestBodySize + 1);
        return bytes.length > maxRequestBodySize ? null : new String(bytes, StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, McpHttpHandler.Response response) throws IOException {
        response.headers().forEach(exchange.getResponseHeaders()::set);
        if (response.body() == null) {
            exchange.sendResponseHeaders(response.statusCode(), -1);
            return;
        }
        byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(response.statusCode(), bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static ExecutorService newExecutor(int maxThreads) {
        AtomicInteger counter = new AtomicInteger();
        // tools may block, so threads are created on demand up to maxThreads; beyond that the
        // accepting thread runs the request itself, which slows down accepting new connections
        return new ThreadPoolExecutor(
                0,
                maxThreads,
                60L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                runnable -> {
                    Thread thread = new Thread(runnable, "mcp-http-server-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private McpServer server;
        private String host;
        private Integer port;
        private String path;
        private Collection<String> allowedOrigins;
        private Integer maxThreads;
        private Integer maxRequestBodySize;

        /**
         * The MCP server to expose. Required.
         */
        public Builder server(McpServer server) {
            this.server = server;
            return this;
        }

        /**
         * The address to bind to. Default: {@code 127.0.0.1}.
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * The port to listen on; {@code 0} picks a free port. Default: {@code 8080}.
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * The path of the MCP endpoint. Default: {@code /mcp}.
         */
        public Builder path(String path) {
            this.path = path;
            return this;
        }

        /**
         * Origins whose browser requests are accepted, see {@link McpHttpHandler#McpHttpHandler(McpServer, Collection)}.
         * Default: none.
         */
        public Builder allowedOrigins(Collection<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
            return this;
        }

        /**
         * The maximum number of requests handled concurrently. Default: {@code 64}.
         */
        public Builder maxThreads(int maxThreads) {
            this.maxThreads = maxThreads;
            return this;
        }

        /**
         * The maximum size of a request body in bytes; larger requests are answered with
         * {@code 413 Content Too Large}. Default: 4 MiB.
         */
        public Builder maxRequestBodySize(int maxRequestBodySize) {
            this.maxRequestBodySize = maxRequestBodySize;
            return this;
        }

        /**
         * Starts the HTTP server.
         */
        public JdkHttpMcpServerTransport build() {
            return new JdkHttpMcpServerTransport(this);
        }
    }
}
