package dev.langchain4j.community.model.qianfan.client;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResponseLoggingInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(ResponseLoggingInterceptor.class);

    public ResponseLoggingInterceptor() {}

    static void log(Response response) {
        try {
            log.debug(
                    "Response:\n- status code: {}\n- headers: {}\n- body: {}",
                    response.code(),
                    RequestLoggingInterceptor.inOneLine(response.headers()),
                    getBody(response));
        } catch (IOException var2) {
            log.warn("Failed to log response", var2);
        }
    }

    private static String getBody(Response response) throws IOException {
        return isEventStream(response)
                ? "[skipping response body due to streaming]"
                : response.peekBody(Long.MAX_VALUE).string();
    }

    private static boolean isEventStream(Response response) {
        String contentType = response.header("content-type");
        return contentType != null && contentType.contains("event-stream");
    }

    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);
        log(response);
        return response;
    }
}
