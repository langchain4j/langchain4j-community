package dev.langchain4j.community.model.xinference.client;

import static dev.langchain4j.community.model.xinference.client.utils.ExceptionUtil.toException;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Function;
import retrofit2.Call;

class AsyncRequestExecutor<Response, ResponseContent> {

    private final Call<Response> call;
    private final Function<Response, ResponseContent> responseContentExtractor;

    AsyncRequestExecutor(Call<Response> call, Function<Response, ResponseContent> responseContentExtractor) {
        this.call = call;
        this.responseContentExtractor = responseContentExtractor;
    }

    AsyncResponseHandling onResponse(Consumer<ResponseContent> responseHandler) {
        return new AsyncResponseHandling() {

            @Override
            public ErrorHandling onError(Consumer<Throwable> errorHandler) {
                return () -> {
                    try {
                        retrofit2.Response<Response> retrofitResponse = AsyncRequestExecutor.this.call.execute();
                        if (retrofitResponse.isSuccessful()) {
                            Response response = retrofitResponse.body();
                            ResponseContent responseContent =
                                    AsyncRequestExecutor.this.responseContentExtractor.apply(response);
                            responseHandler.accept(responseContent);
                        } else {
                            errorHandler.accept(toException(retrofitResponse));
                        }
                    } catch (IOException e) {
                        errorHandler.accept(e);
                    }
                    return new ResponseHandle();
                };
            }

            @Override
            public ErrorHandling ignoreErrors() {
                return () -> {
                    try {
                        retrofit2.Response<Response> retrofitResponse = AsyncRequestExecutor.this.call.execute();
                        if (retrofitResponse.isSuccessful()) {
                            Response response = retrofitResponse.body();
                            ResponseContent responseContent =
                                    AsyncRequestExecutor.this.responseContentExtractor.apply(response);
                            responseHandler.accept(responseContent);
                        }
                    } catch (IOException e) {
                        // intentionally ignoring, because user called ignoreErrors()
                    }
                    return new ResponseHandle();
                };
            }
        };
    }
}
