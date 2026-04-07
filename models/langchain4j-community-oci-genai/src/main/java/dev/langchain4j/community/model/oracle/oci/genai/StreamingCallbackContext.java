package dev.langchain4j.community.model.oracle.oci.genai;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

interface StreamingCallbackContext {

    Map<Thread, AtomicInteger> streamingCallbackThreads();

    default void runInStreamingCallbackContext(Runnable callback) {
        Thread callbackThread = Thread.currentThread();
        streamingCallbackThreads().compute(callbackThread, (thread, depth) -> {
            if (depth == null) {
                return new AtomicInteger(1);
            }
            depth.incrementAndGet();
            return depth;
        });
        try {
            callback.run();
        } finally {
            streamingCallbackThreads()
                    .computeIfPresent(callbackThread, (thread, depth) -> depth.decrementAndGet() <= 0 ? null : depth);
        }
    }

    default boolean isCurrentThreadInStreamingCallbackContext() {
        return streamingCallbackThreads().containsKey(Thread.currentThread());
    }
}
