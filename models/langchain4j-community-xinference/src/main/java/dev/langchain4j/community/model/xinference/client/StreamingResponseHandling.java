package dev.langchain4j.community.model.xinference.client;

public interface StreamingResponseHandling extends AsyncResponseHandling {
    StreamingCompletionHandling onComplete(Runnable streamingCompletionCallback);
}
