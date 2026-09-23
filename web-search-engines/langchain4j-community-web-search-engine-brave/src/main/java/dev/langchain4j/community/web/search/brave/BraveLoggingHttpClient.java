package dev.langchain4j.community.web.search.brave;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BraveLoggingHttpClient implements HttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(BraveLoggingHttpClient.class);

    private final HttpClient delegate;
    private final boolean logRequests;
    private final boolean logResponses;
    private final String apiKey;

    BraveLoggingHttpClient(HttpClient delegate, Boolean logRequests, Boolean logResponses, String apiKey) {
        this.delegate = delegate;
        this.logRequests = Boolean.TRUE.equals(logRequests);
        this.logResponses = Boolean.TRUE.equals(logResponses);
        this.apiKey = apiKey;
    }

    @Override
    public SuccessfulHttpResponse execute(HttpRequest request) {
        logRequest(request);
        SuccessfulHttpResponse response = delegate.execute(request);
        if (logResponses) {
            LOG.info(
                    "HTTP response:\n- status code: {}\n- headers: {}\n- body: {}",
                    response.statusCode(),
                    response.headers(),
                    response.body());
        }
        return response;
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventListener listener) {
        logRequest(request);
        delegate.execute(request, listener);
    }

    @Override
    public void execute(HttpRequest request, ServerSentEventParser parser, ServerSentEventListener listener) {
        logRequest(request);
        delegate.execute(request, parser, listener);
    }

    private void logRequest(HttpRequest request) {
        if (logRequests) {
            LOG.info(
                    "HTTP request:\n- method: {}\n- url: {}\n- headers: {}\n- body: {}",
                    request.method(),
                    BraveBuilder.maskUrl(request.url(), apiKey),
                    maskHeaders(request.headers()),
                    request.body());
        }
    }

    private static Map<String, List<String>> maskHeaders(Map<String, List<String>> headers) {
        return headers.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
            if (BraveBuilder.isSensitive(entry.getKey())) {
                return entry.getValue().stream().map(BraveBuilder::maskSecret).collect(Collectors.toList());
            }
            return entry.getValue();
        }));
    }
}
