package dev.langchain4j.community.model.qianfan.client;

public interface StreamingResponseHandling extends AsyncResponseHandling {

    StreamingCompletionHandling onComplete(Runnable runnable);
}
