package dev.langchain4j.community.model.minimax.client.utils;

import dev.langchain4j.community.model.minimax.client.MiniMaxHttpException;
import java.io.IOException;

public final class ExceptionUtil {
    public static RuntimeException toException(retrofit2.Response<?> response) throws IOException {
        return new MiniMaxHttpException(response.code(), response.errorBody().string());
    }

    public static RuntimeException toException(okhttp3.Response response) throws IOException {
        return new MiniMaxHttpException(response.code(), response.body().string());
    }
}
