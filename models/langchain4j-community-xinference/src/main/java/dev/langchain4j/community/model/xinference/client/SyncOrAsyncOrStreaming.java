package dev.langchain4j.community.model.xinference.client;

import java.util.function.Consumer;

public interface SyncOrAsyncOrStreaming<ResponseContent> extends SyncOrAsync<ResponseContent> {
    StreamingResponseHandling onPartialResponse(Consumer<ResponseContent> partialResponseHandler);
}
