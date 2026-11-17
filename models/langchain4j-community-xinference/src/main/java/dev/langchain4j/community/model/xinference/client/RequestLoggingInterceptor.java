package dev.langchain4j.community.model.xinference.client;

import dev.langchain4j.internal.Utils;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RequestLoggingInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingInterceptor.class);

    /**
     * A header whose name contains any of these is assumed to carry a credential.
     */
    private static final List<String> SECRET_HEADER_NAME_KEYWORDS =
            List.of("auth", "key", "token", "secret", "password", "credential", "cookie", "signature", "session");

    private static final int MIN_LENGTH_TO_SHOW_PARTIALLY = 20;

    private static String getBody(Request request) {
        try {
            Buffer buffer = new Buffer();
            if (request.body() == null) {
                return "";
            }
            request.body().writeTo(buffer);
            return buffer.readUtf8();
        } catch (Exception e) {
            log.warn("Exception while getting body", e);
            return "Exception while getting body: " + e.getMessage();
        }
    }

    static String getHeaders(Headers headers) {
        return StreamSupport.stream(headers.spliterator(), false)
                .map(header -> formatHeader(header.component1(), header.component2()))
                .collect(Collectors.joining(", "));
    }

    private static String formatHeader(String headerKey, String headerValue) {
        if (isSecretHeader(headerKey)) {
            headerValue = maskSecretKey(headerValue);
        }
        return String.format("[%s: %s]", headerKey, headerValue);
    }

    private static boolean isSecretHeader(String headerKey) {
        String lowerCaseHeaderKey = headerKey.toLowerCase(Locale.ROOT);
        return SECRET_HEADER_NAME_KEYWORDS.stream().anyMatch(lowerCaseHeaderKey::contains);
    }

    private static String maskSecretKey(String key) {
        if (Utils.isNullOrBlank(key)) {
            return key;
        }

        if (key.length() >= MIN_LENGTH_TO_SHOW_PARTIALLY) {
            return key.substring(0, 5) + "..." + key.substring(key.length() - 2);
        } else {
            return "...";
        }
    }

    @Override
    public @NotNull Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        this.log(request);
        return chain.proceed(request);
    }

    private void log(Request request) {
        try {
            log.debug(
                    "Request:\n- method: {}\n- url: {}\n- headers: {}\n- body: {}",
                    request.method(),
                    request.url(),
                    getHeaders(request.headers()),
                    getBody(request));
        } catch (Exception e) {
            log.warn("Error while logging request: {}", e.getMessage());
        }
    }
}
