package dev.langchain4j.community.data.document.parser.llamaparse;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

class ApiKeyInsertingInterceptor implements Interceptor {

    private final String apiKey;

    ApiKeyInsertingInterceptor(String apiKey) {
        this.apiKey = ensureNotBlank(apiKey, "apiKey");
    }

    @Override
    public Response intercept(Chain chain) throws IOException {

        Request request = chain.request()
                .newBuilder()
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        return chain.proceed(request);
    }
}
