package dev.langchain4j.community.mcp.server.spring;

import dev.langchain4j.community.mcp.server.transport.StdioMcpServerTransport;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.SmartLifecycle;

/**
 * Owns one STDIO transport and keeps a non-web application alive until EOF or shutdown.
 * A STDIO session cannot be restarted after it has been stopped.
 */
public class McpServerLifecycle implements SmartLifecycle, ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(McpServerLifecycle.class);

    private final Supplier<StdioMcpServerTransport> transportSupplier;
    private final ConfigurableApplicationContext context;
    private StdioMcpServerTransport transport;
    private Thread waiter;
    private boolean started;
    private volatile boolean running;

    public McpServerLifecycle(
            Supplier<StdioMcpServerTransport> transportSupplier, ConfigurableApplicationContext context) {
        this.transportSupplier = transportSupplier;
        this.context = context;
    }

    @Override
    public boolean isAutoStartup() {
        return false;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (event.getApplicationContext() == context) {
            start();
        }
    }

    @Override
    public synchronized void start() {
        if (started) {
            return;
        }
        transport = transportSupplier.get();
        started = true;
        running = true;
        waiter = new Thread(this::awaitClose, "mcp-stdio-application");
        waiter.setDaemon(false);
        waiter.start();
    }

    private void awaitClose() {
        try {
            transport.awaitClose();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (running) {
                LOG.error("Interrupted while awaiting MCP STDIO shutdown", e);
            }
        } finally {
            if (running) {
                context.close();
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        // Release the non-daemon waiter even if an input stream cannot unblock its daemon reader.
        if (waiter != Thread.currentThread()) {
            waiter.interrupt();
        }
        try {
            transport.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to close MCP STDIO transport", e);
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
