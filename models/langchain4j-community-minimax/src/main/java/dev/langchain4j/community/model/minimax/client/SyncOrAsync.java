package dev.langchain4j.community.model.minimax.client;

import java.util.function.Consumer;

public interface SyncOrAsync<ResponseContent> {
    ResponseContent execute();

    AsyncResponseHandling onResponse(Consumer<ResponseContent> responseHandler);
}
