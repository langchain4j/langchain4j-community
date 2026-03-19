package dev.langchain4j.community.model.minimax.client;

import java.util.function.Consumer;

public interface StreamingCompletionHandling {
    ErrorHandling onError(Consumer<Throwable> errorHandler);

    ErrorHandling ignoreErrors();
}
