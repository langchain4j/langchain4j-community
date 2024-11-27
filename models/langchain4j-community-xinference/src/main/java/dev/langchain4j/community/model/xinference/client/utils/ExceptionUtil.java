package dev.langchain4j.community.model.xinference.client.utils;

import dev.langchain4j.community.model.xinference.client.XinferenceHttpException;

import java.io.IOException;

public final class ExceptionUtil {
    public static RuntimeException toException(retrofit2.Response<?> response) throws IOException {
        return new XinferenceHttpException(response.code(), response.errorBody().string());
    }

    public static RuntimeException toException(okhttp3.Response response) throws IOException {
        return new XinferenceHttpException(response.code(), response.body().string());
    }
}
