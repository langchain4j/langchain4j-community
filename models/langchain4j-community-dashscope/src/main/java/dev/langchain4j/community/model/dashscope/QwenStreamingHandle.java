package dev.langchain4j.community.model.dashscope;

import dev.langchain4j.model.chat.response.StreamingHandle;

public class QwenStreamingHandle implements StreamingHandle {
    private volatile boolean isCancelled;

    @Override
    public void cancel() {
        isCancelled = true;
    }

    @Override
    public boolean isCancelled() {
        return isCancelled;
    }
}
