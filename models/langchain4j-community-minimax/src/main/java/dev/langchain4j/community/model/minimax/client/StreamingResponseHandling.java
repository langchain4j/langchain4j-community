package dev.langchain4j.community.model.minimax.client;

public interface StreamingResponseHandling extends AsyncResponseHandling {
    StreamingCompletionHandling onComplete(Runnable streamingCompletionCallback);
}
