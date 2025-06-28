package dev.langchain4j.community.model.qianfan.client;

import static dev.langchain4j.community.model.qianfan.client.Json.fromJson;
import static dev.langchain4j.community.model.qianfan.client.Json.toJson;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StreamingRequestExecutor<Request, Response, ResponseContent> {

    private static final Logger log = LoggerFactory.getLogger(StreamingRequestExecutor.class);
    private final OkHttpClient okHttpClient;
    private final String endpointUrl;
    private final Supplier<Request> requestWithStreamSupplier;
    private final Class<Response> responseClass;
    private final Function<Response, ResponseContent> streamEventContentExtractor;
    private final boolean logStreamingResponses;

    StreamingRequestExecutor(
            OkHttpClient okHttpClient,
            String endpointUrl,
            Supplier<Request> requestWithStreamSupplier,
            Class<Response> responseClass,
            Function<Response, ResponseContent> streamEventContentExtractor,
            boolean logStreamingResponses) {
        this.okHttpClient = okHttpClient;
        this.endpointUrl = endpointUrl;
        this.requestWithStreamSupplier = requestWithStreamSupplier;
        this.responseClass = responseClass;
        this.streamEventContentExtractor = streamEventContentExtractor;
        this.logStreamingResponses = logStreamingResponses;
    }

    StreamingResponseHandling onPartialResponse(Consumer<ResponseContent> partialResponseHandler) {
        return new StreamingResponseHandling() {
            public StreamingCompletionHandling onComplete(Runnable runnable) {
                return new StreamingCompletionHandling() {
                    public ErrorHandling onError(Consumer<Throwable> errorHandler) {
                        return () -> stream(partialResponseHandler, runnable, errorHandler);
                    }

                    public ErrorHandling ignoreErrors() {
                        return () -> stream(partialResponseHandler, runnable, e -> {});
                    }
                };
            }

            public ErrorHandling onError(Consumer<Throwable> errorHandler) {
                return () -> stream(partialResponseHandler, () -> {}, errorHandler);
            }

            public ErrorHandling ignoreErrors() {
                return () -> stream(partialResponseHandler, () -> {}, (e) -> {});
            }
        };
    }

    private void stream(
            Consumer<ResponseContent> partialResponseHandler,
            Runnable streamingCompletionCallback,
            Consumer<Throwable> errorHandler) {
        Request request = this.requestWithStreamSupplier.get();
        String requestJson = toJson(request);
        okhttp3.Request okHttpRequest = (new okhttp3.Request.Builder())
                .url(this.endpointUrl)
                .post(RequestBody.create(requestJson, MediaType.get("application/json; charset=utf-8")))
                .build();
        EventSourceListener eventSourceListener = new EventSourceListener() {
            public void onOpen(EventSource eventSource, okhttp3.Response response) {
                if (logStreamingResponses) {
                    ResponseLoggingInterceptor.log(response);
                }
            }

            public void onEvent(EventSource eventSource, String id, String type, String data) {
                if (logStreamingResponses) {
                    log.debug("onEvent() {}", data);
                }

                if (!"[DONE]".equals(data)) {
                    try {
                        Response response = fromJson(data, responseClass);
                        ResponseContent responseContent = streamEventContentExtractor.apply(response);
                        if (responseContent != null) {
                            partialResponseHandler.accept(responseContent);
                        }
                    } catch (Exception var7) {
                        errorHandler.accept(var7);
                    }
                }
            }

            public void onClosed(EventSource eventSource) {
                if (logStreamingResponses) {
                    log.debug("onClosed()");
                }
                streamingCompletionCallback.run();
            }

            public void onFailure(EventSource eventSource, Throwable t, okhttp3.Response response) {
                if (logStreamingResponses) {

                    log.debug("reqeust url:", response.request().url());
                    log.debug("onFailure()", t);
                    ResponseLoggingInterceptor.log(response);
                }

                if (t != null) {
                    errorHandler.accept(t);
                } else {
                    try {
                        errorHandler.accept(Utils.toException(response));
                    } catch (IOException var5) {
                        errorHandler.accept(var5);
                    }
                }
            }
        };
        EventSources.createFactory(this.okHttpClient).newEventSource(okHttpRequest, eventSourceListener);
    }
}
