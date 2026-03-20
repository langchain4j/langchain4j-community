package dev.langchain4j.community.model.minimax.client;

import dev.langchain4j.community.model.minimax.client.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.minimax.client.chat.ChatCompletionResponse;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface MiniMaxApi {

    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    Call<ChatCompletionResponse> chatCompletions(@Body ChatCompletionRequest request);
}
