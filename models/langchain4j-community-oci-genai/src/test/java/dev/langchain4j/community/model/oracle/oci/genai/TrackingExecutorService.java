package dev.langchain4j.community.model.oracle.oci.genai;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

final class TrackingExecutorService extends AbstractExecutorService {

    private final ExecutorService delegate;
    private final AtomicBoolean shutdownCalled = new AtomicBoolean();
    private final AtomicBoolean shutdownNowCalled = new AtomicBoolean();

    TrackingExecutorService(String threadNamePrefix) {
        var threadCounter = new AtomicInteger();
        this.delegate = Executors.newSingleThreadExecutor(command -> {
            var thread = new Thread(command, threadNamePrefix + "-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    boolean shutdownWasRequested() {
        return shutdownCalled.get() || shutdownNowCalled.get();
    }

    void shutdownDelegateNow() {
        delegate.shutdownNow();
    }

    @Override
    public void shutdown() {
        shutdownCalled.set(true);
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdownNowCalled.set(true);
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        delegate.execute(command);
    }
}
