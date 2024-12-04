package dev.langchain4j.community.model.xinference.client;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ResponseLoggingInterceptor implements Interceptor {
    private static final Logger log = LoggerFactory.getLogger(ResponseLoggingInterceptor.class);

    public ResponseLoggingInterceptor() {}

    private static boolean isEventStream(Response response) {
        String contentType = response.header("Content-Type");
        return contentType != null && contentType.contains("event-stream");
    }

    @Override
    public @NotNull Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);
        this.log(response);
        return response;
    }

    public void log(Response response) {
        try {
            log(
                    "Response:\n- status code: {}\n- headers: {}\n- body: {}",
                    response.code(),
                    response.headers(),
                    this.getBody(response));
        } catch (Exception e) {
            log.warn("Error while logging response: {}", e.getMessage());
        }
    }

    public void log(String message, Object... args) {
        log.debug(message, args);
    }

    private String getBody(Response response) throws IOException {
        return isEventStream(response)
                ? "[skipping response body due to streaming]"
                : response.peekBody(Long.MAX_VALUE).string();
    }
}
