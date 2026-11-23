package dev.langchain4j.community.model.qianfan.client;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okio.Buffer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestLoggingInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingInterceptor.class);

    /**
     * A header, query parameter or JSON field whose name contains any of these is assumed to carry a credential.
     * {@code client_id} is listed because it carries the API key when requesting an access token.
     */
    private static final List<String> SECRET_NAME_KEYWORDS = List.of(
            "auth", "key", "token", "secret", "password", "credential", "cookie", "signature", "session", "client_id");

    private static final int MIN_LENGTH_TO_SHOW_PARTIALLY = 20;

    public RequestLoggingInterceptor() {}

    private static void log(Request request) {
        try {
            log.debug(
                    "Request:\n- method: {}\n- url: {}\n- headers: {}\n- body: {}",
                    request.method(),
                    maskUrl(request.url()),
                    inOneLine(request.headers()),
                    getBody(request));
        } catch (Exception var2) {
            log.warn("Failed to log request", var2);
        }
    }

    static String inOneLine(Headers headers) {
        return StreamSupport.stream(headers.spliterator(), false)
                .map((header) -> {
                    String headerKey = header.component1();
                    String headerValue = header.component2();
                    if (isSecret(headerKey)) {
                        headerValue = maskSecret(headerValue);
                    }

                    return String.format("[%s: %s]", headerKey, headerValue);
                })
                .collect(Collectors.joining(", "));
    }

    static String maskUrl(HttpUrl url) {
        HttpUrl.Builder maskedUrl = url.newBuilder().query(null);
        for (int i = 0; i < url.querySize(); i++) {
            String name = url.queryParameterName(i);
            String value = url.queryParameterValue(i);
            maskedUrl.addQueryParameter(name, isSecret(name) ? maskSecret(value) : value);
        }
        return maskedUrl.build().toString();
    }

    static boolean isSecret(String name) {
        String lowerCaseName = name.toLowerCase(Locale.ROOT);
        return SECRET_NAME_KEYWORDS.stream().anyMatch(lowerCaseName::contains);
    }

    static @Nullable String maskSecret(@Nullable String secret) {
        if (secret == null || secret.isBlank()) {
            return secret;
        }
        if (secret.length() >= MIN_LENGTH_TO_SHOW_PARTIALLY) {
            return secret.substring(0, 5) + "..." + secret.substring(secret.length() - 2);
        }
        return "...";
    }

    private static String getBody(Request request) {
        if ("GET".equals(request.method())) {
            return "";
        }
        try {
            Buffer buffer = new Buffer();
            request.body().writeTo(buffer);
            return buffer.readUtf8();
        } catch (Exception var2) {
            log.warn("Exception happened while reading request body", var2);
            return "[Exception happened while reading request body. Check logs for more details.]";
        }
    }

    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        log(request);
        return chain.proceed(request);
    }
}
