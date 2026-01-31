package dev.langchain4j.community.mcp.server.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.mcp.client.McpCallContext;
import dev.langchain4j.mcp.client.transport.McpOperationHandler;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.protocol.McpClientMessage;
import dev.langchain4j.mcp.protocol.McpInitializationNotification;
import dev.langchain4j.mcp.protocol.McpInitializeRequest;
import dev.langchain4j.mcp.transport.stdio.JsonRpcIoHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

public class InMemoryMcpTransport implements McpTransport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final InputStream input;
    private final OutputStream output;

    private McpOperationHandler messageHandler;
    private JsonRpcIoHandler ioHandler;

    public InMemoryMcpTransport(InputStream input, OutputStream output) {
        this.input = input;
        this.output = output;
    }

    @Override
    public void start(McpOperationHandler messageHandler) {
        this.messageHandler = messageHandler;
        this.ioHandler = new JsonRpcIoHandler(input, output, messageHandler::handle, false);
        Thread thread = new Thread(ioHandler, "mcp-client-stdio");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public CompletableFuture<JsonNode> initialize(McpInitializeRequest request) {
        try {
            String requestString = OBJECT_MAPPER.writeValueAsString(request);
            String initializationNotification = OBJECT_MAPPER.writeValueAsString(new McpInitializationNotification());
            return execute(requestString, request.getId())
                    .thenCompose(originalResponse -> execute(initializationNotification, null)
                            .thenCompose(ignored -> CompletableFuture.completedFuture(originalResponse)));
        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<JsonNode> executeOperationWithResponse(McpClientMessage request) {
        try {
            String requestString = OBJECT_MAPPER.writeValueAsString(request);
            return execute(requestString, request.getId());
        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public CompletableFuture<JsonNode> executeOperationWithResponse(McpCallContext context) {
        return executeOperationWithResponse(context.message());
    }

    @Override
    public void executeOperationWithoutResponse(McpClientMessage request) {
        try {
            String requestString = OBJECT_MAPPER.writeValueAsString(request);
            execute(requestString, null);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void executeOperationWithoutResponse(McpCallContext context) {
        executeOperationWithoutResponse(context.message());
    }

    @Override
    public void checkHealth() {
        // No-op: in-memory transport has no external process to check.
    }

    @Override
    public void onFailure(Runnable actionOnFailure) {
        // No-op: in-memory transport does not support reconnection.
    }

    @Override
    public void close() throws IOException {
        if (ioHandler != null) {
            ioHandler.close();
        }
    }

    private CompletableFuture<JsonNode> execute(String request, Long id) {
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        if (id != null) {
            messageHandler.startOperation(id, future);
        }
        try {
            ioHandler.submit(request);
            if (id == null) {
                future.complete(null);
            }
        } catch (IOException e) {
            future.completeExceptionally(e);
        }
        return future;
    }
}
