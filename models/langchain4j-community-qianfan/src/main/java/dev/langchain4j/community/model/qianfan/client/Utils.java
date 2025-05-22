package dev.langchain4j.community.model.qianfan.client;

import dev.langchain4j.Internal;
import java.io.IOException;
import retrofit2.Response;

@Internal
class Utils {

    private Utils() throws InstantiationException {
        throw new InstantiationException("Can not instantiate utility class");
    }

    static RuntimeException toException(Response<?> response) throws IOException {
        return new QianfanHttpException(response.code(), response.errorBody().string());
    }

    static RuntimeException toException(okhttp3.Response response) throws IOException {
        return new QianfanHttpException(response.code(), response.body().string());
    }
}
