package dev.langchain4j.community.model.xinference.client;

import java.util.function.Consumer;

public interface SyncOrAsync<ResponseContent> {
    ResponseContent execute();

    AsyncResponseHandling onResponse(Consumer<ResponseContent> responseHandler);
}
