package dev.langchain4j.community.model.xinference.client;

import dev.langchain4j.community.model.xinference.client.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.xinference.client.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.xinference.client.completion.CompletionRequest;
import dev.langchain4j.community.model.xinference.client.completion.CompletionResponse;
import dev.langchain4j.community.model.xinference.client.embedding.EmbeddingRequest;
import dev.langchain4j.community.model.xinference.client.embedding.EmbeddingResponse;
import dev.langchain4j.community.model.xinference.client.image.ImageRequest;
import dev.langchain4j.community.model.xinference.client.image.ImageResponse;
import dev.langchain4j.community.model.xinference.client.rerank.RerankRequest;
import dev.langchain4j.community.model.xinference.client.rerank.RerankResponse;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface XinferenceApi {
    @POST("v1/completions")
    @Headers("Content-Type: application/json")
    Call<CompletionResponse> completions(@Body CompletionRequest request);

    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    Call<ChatCompletionResponse> chatCompletions(@Body ChatCompletionRequest request);

    @POST("v1/embeddings")
    @Headers("Content-Type: application/json")
    Call<EmbeddingResponse> embeddings(@Body EmbeddingRequest request);

    @POST("v1/rerank")
    @Headers("Content-Type: application/json")
    Call<RerankResponse> rerank(@Body RerankRequest request);

    @POST("v1/images/generations")
    @Headers({"Content-Type: application/json"})
    Call<ImageResponse> generations(@Body ImageRequest request);

    @POST("v1/images/variations")
    Call<ImageResponse> variations(@Body RequestBody request);

    @POST("v1/images/inpainting")
    Call<ImageResponse> inpainting(@Body RequestBody request);

    @POST("v1/images/ocr")
    Call<String> ocr(@Body RequestBody request);
}
