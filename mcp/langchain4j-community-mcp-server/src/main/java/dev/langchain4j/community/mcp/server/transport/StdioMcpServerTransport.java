package dev.langchain4j.community.mcp.server.transport;

import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.community.mcp.server.McpServer;
import dev.langchain4j.mcp.protocol.McpJsonRpcMessage;
import dev.langchain4j.mcp.transport.stdio.JsonRpcIoHandler;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * WARNING: When using this transport with {@code System.out}, ensure that your application logger
 * (e.g., SLF4J, Logback) is configured to write to {@code System.err} instead of {@code System.out}.
 * Any extraneous output to stdout will corrupt the JSON-RPC protocol stream and cause the client
 * to disconnect.
 */
public class StdioMcpServerTransport implements Closeable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int DEFAULT_MAX_PENDING_MESSAGES = 64;

    private final McpServer server;
    private final JsonRpcIoHandler ioHandler;
    private final Thread ioThread;
    private final ExecutorService messageExecutor;
    private final Object submitLock = new Object();

    public StdioMcpServerTransport(InputStream input, OutputStream output, McpServer server) {
        this.server = ensureNotNull(server, "server");
        ensureNotNull(input, "input");
        ensureNotNull(output, "output");
        this.messageExecutor = new ThreadPoolExecutor(
                1,
                1,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(DEFAULT_MAX_PENDING_MESSAGES),
                runnable -> {
                    Thread thread = new Thread(runnable, "mcp-stdio-server-dispatcher");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.CallerRunsPolicy());
        this.ioHandler = new JsonRpcIoHandler(input, output, this::handleMessage, false);
        this.ioThread = new Thread(ioHandler, "mcp-stdio-server");
        this.ioThread.setDaemon(true);
        this.ioThread.start();
    }

    /**
     * WARNING: When using this transport with {@code System.out}, ensure that your application logger
     * (e.g., SLF4J, Logback) is configured to write to {@code System.err} instead of {@code System.out}.
     * Any extraneous output to stdout will corrupt the JSON-RPC protocol stream and cause the client
     * to disconnect.
     */
    @SuppressWarnings("java:S106")
    public StdioMcpServerTransport(McpServer server) {
        this(System.in, System.out, server);
    }

    private void handleMessage(JsonNode message) {
        try {
            messageExecutor.execute(() -> handleMessageInternal(message));
        } catch (RejectedExecutionException ignored) {
            // ignore: transport is closing
        }
    }

    private void handleMessageInternal(JsonNode message) {
        McpJsonRpcMessage response = server.handle(message);
        if (response == null) {
            return;
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(response);
            synchronized (submitLock) {
                ioHandler.submit(json);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            ioHandler.close();
        } finally {
            messageExecutor.shutdownNow();
        }
    }
}
