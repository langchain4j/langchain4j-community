package dev.langchain4j.community.mcp.server.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.langchain4j.community.mcp.server.McpServer;
import dev.langchain4j.community.mcp.server.transport.StdioMcpServerTransport;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

class McpServerLifecycleTest {

    @Test
    void defersStartupUntilOwningApplicationIsReady() throws Exception {
        var transport = mock(StdioMcpServerTransport.class);
        doAnswer(invocation -> {
                    new CountDownLatch(1).await();
                    return null;
                })
                .when(transport)
                .awaitClose();
        var context = mock(ConfigurableApplicationContext.class);
        var creations = new AtomicInteger();
        var lifecycle = new McpServerLifecycle(
                () -> {
                    creations.incrementAndGet();
                    return transport;
                },
                context);
        try {
            assertThat(lifecycle.isAutoStartup()).isFalse();
            lifecycle.onApplicationEvent(new ApplicationReadyEvent(
                    new SpringApplication(), new String[0], mock(ConfigurableApplicationContext.class), Duration.ZERO));
            assertThat(creations).hasValue(0);
            var ready = new ApplicationReadyEvent(new SpringApplication(), new String[0], context, Duration.ZERO);
            lifecycle.onApplicationEvent(ready);
            lifecycle.onApplicationEvent(ready);
            assertThat(creations).hasValue(1);
        } finally {
            lifecycle.stop();
        }
    }

    @Test
    void startsAndStopsOnceAndReleasesNonDaemonWaiter() throws Exception {
        var transport = mock(StdioMcpServerTransport.class);
        var context = mock(ConfigurableApplicationContext.class);
        var entered = new CountDownLatch(1);
        var thread = new AtomicReference<Thread>();
        doAnswer(invocation -> {
                    thread.set(Thread.currentThread());
                    entered.countDown();
                    new CountDownLatch(1).await();
                    return null;
                })
                .when(transport)
                .awaitClose();
        var creations = new AtomicInteger();
        var lifecycle = new McpServerLifecycle(
                () -> {
                    creations.incrementAndGet();
                    return transport;
                },
                context);
        try {
            lifecycle.start();
            lifecycle.start();
            assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(thread.get().isDaemon()).isFalse();
            assertThat(lifecycle.isRunning()).isTrue();
            lifecycle.stop();
            lifecycle.stop();
            lifecycle.start();
            thread.get().join(5_000);
            assertThat(thread.get().isAlive()).isFalse();
            assertThat(lifecycle.isRunning()).isFalse();
            assertThat(creations).hasValue(1);
            verify(transport, times(1)).close();
            verify(context, times(0)).close();
        } finally {
            lifecycle.stop();
        }
    }

    @Test
    void eofClosesApplicationAndTransport() throws Exception {
        try (var input = new PipedInputStream();
                var client = new PipedOutputStream(input);
                var output = new ByteArrayOutputStream();
                var context = new GenericApplicationContext()) {
            var transport = new StdioMcpServerTransport(input, output, new McpServer(List.of()));
            var lifecycle = new McpServerLifecycle(() -> transport, context);
            var interruptedOnClose = new AtomicReference<Boolean>();
            context.registerBean(McpServerLifecycle.class, () -> lifecycle);
            context.getDefaultListableBeanFactory()
                    .registerDisposableBean(
                            "closeProbe",
                            () -> interruptedOnClose.set(Thread.currentThread().isInterrupted()));
            context.refresh();
            lifecycle.start();
            assertThat(lifecycle.isRunning()).isTrue();
            client.close();
            await().atMost(Duration.ofSeconds(5)).until(() -> !context.isActive() && !lifecycle.isRunning());
            await().atMost(Duration.ofSeconds(5)).until(() -> interruptedOnClose.get() != null);
            assertThat(interruptedOnClose.get()).isFalse();
        }
    }

    @Test
    void stopSurfacesCloseFailureAndStillReleasesWaiter() throws Exception {
        var transport = mock(StdioMcpServerTransport.class);
        var thread = new AtomicReference<Thread>();
        doAnswer(invocation -> {
                    thread.set(Thread.currentThread());
                    new CountDownLatch(1).await();
                    return null;
                })
                .when(transport)
                .awaitClose();
        doThrow(new IOException("close failure")).when(transport).close();
        var lifecycle = new McpServerLifecycle(() -> transport, mock(ConfigurableApplicationContext.class));
        lifecycle.start();
        await().atMost(Duration.ofSeconds(5)).until(() -> thread.get() != null);
        assertThatThrownBy(lifecycle::stop)
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Failed to close MCP STDIO transport");
        thread.get().join(5_000);
        assertThat(thread.get().isAlive()).isFalse();
        assertThat(lifecycle.isRunning()).isFalse();
    }
}
