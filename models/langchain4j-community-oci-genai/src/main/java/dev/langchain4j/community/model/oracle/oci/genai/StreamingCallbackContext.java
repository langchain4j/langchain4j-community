package dev.langchain4j.community.model.oracle.oci.genai;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class StreamingCallbackContext {

    private final BaseChatModel<?> model;
    private final ConcurrentHashMap<Thread, AtomicInteger> callbackThreads = new ConcurrentHashMap<>();

    StreamingCallbackContext(BaseChatModel<?> model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    /**
     * Runs a callback while marking the current thread as a streaming callback thread.
     *
     * @param callback callback body to execute
     */
    void runInStreamingCallbackContext(Runnable callback) {
        Thread callbackThread = Thread.currentThread();
        callbackThreads.compute(callbackThread, (thread, depth) -> {
            if (depth == null) {
                return new AtomicInteger(1);
            }
            depth.incrementAndGet();
            return depth;
        });
        try {
            callback.run();
        } finally {
            callbackThreads.computeIfPresent(
                    callbackThread, (thread, depth) -> depth.decrementAndGet() <= 0 ? null : depth);
        }
    }

    /**
     * Checks if the current thread is currently executing in a streaming callback context.
     *
     * @return {@code true} when current thread is marked as callback thread
     */
    boolean isCurrentThreadInStreamingCallbackContext() {
        return callbackThreads.containsKey(Thread.currentThread());
    }

    void close() {
        model.closeModel(isCurrentThreadInStreamingCallbackContext());
    }
}
