package dev.langchain4j.community.model.novitaai.client;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Streaming;

/**
 * Public interface to interact with the Novita AI API.
 */
public interface NovitaAiApi {

    /**
     * Generate chat.
     *
     * @param apiRequest
     *      request.
     * @return
     *      response.
     */
    @POST("chat/completions")
    @Headers({"Content-Type: application/json"})
    Call<NovitaAiChatCompletionResponse> generateChat(@Body NovitaAiChatCompletionRequest apiRequest);

    /**
     * Generate chat.
     *
     * @param apiRequest
     *      request.
     * @return
     *      response.
     */
    @POST("chat/completions")
    @Headers({"Content-Type: application/json"})
    @Streaming
    Call<NovitaAiChatCompletionResponse> streamingChatCompletion(@Body NovitaAiChatCompletionRequest apiRequest);

}
