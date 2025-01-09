package dev.langchain4j.community.model.zhipu;

import dev.langchain4j.community.model.zhipu.assistant.AssistantCompletion;
import dev.langchain4j.community.model.zhipu.assistant.AssistantSupportResponse;
import dev.langchain4j.community.model.zhipu.assistant.conversation.ConversationRequest;
import dev.langchain4j.community.model.zhipu.assistant.conversation.ConversationResponse;
import dev.langchain4j.community.model.zhipu.assistant.problem.ProblemsResponse;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.zhipu.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.zhipu.embedding.EmbeddingRequest;
import dev.langchain4j.community.model.zhipu.embedding.EmbeddingResponse;
import dev.langchain4j.community.model.zhipu.image.ImageRequest;
import dev.langchain4j.community.model.zhipu.image.ImageResponse;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Streaming;

interface ZhipuAiApi {

    @POST("api/paas/v4/chat/completions")
    @Headers({"Content-Type: application/json"})
    Call<ChatCompletionResponse> chatCompletion(@Body ChatCompletionRequest request);

    @Streaming
    @POST("api/paas/v4/chat/completions")
    @Headers({"Content-Type: application/json"})
    Call<ResponseBody> streamingChatCompletion(@Body ChatCompletionRequest request);

    @POST("api/paas/v4/embeddings")
    @Headers({"Content-Type: application/json"})
    Call<EmbeddingResponse> embeddings(@Body EmbeddingRequest request);

    @POST("api/paas/v4/images/generations")
    @Headers({"Content-Type: application/json"})
    Call<ImageResponse> generations(@Body ImageRequest request);

    /**
     * 3.1 获取智能体（智能体（应用））输入参数
     * @param appId 智能体（应用）id
     * @return AssistantSupportResponse
     */
    @GET("api/llm-application/open/v2/application/{app_id}/variables")
    @Headers({"Content-Type: application/json"})
    Call<AssistantSupportResponse> variables(@Path("app_id") String appId);

    /**
     * 3.4 创建新会话
     * @param appId 智能体（应用）id
     * @return ConversationResponse 使用data中的conversationId字段
     */
    @POST("api/llm-application/open/v2/application/{app_id}/conversation")
    @Headers({"Content-Type: application/json"})
    Call<ConversationResponse> conversation(@Path("app_id") String appId);
    /**
     * 3.5 创建对话或创作请求
     * @param request 接口请求
     * @return ConversationResponse - 使用data中的id字段
     */
    @POST("api/llm-application/open/v2/application/generate_request_id")
    @Headers({"Content-Type: application/json"})
    Call<ConversationResponse> generateRequestId(@Body ConversationRequest request);

    /**
     * 3.6 获取对话或创作结果 (sse对话)
     * @param id 创建对话或创作请求 接口返回的id
     * @return AssistantCompletionResponse
     */
    @Streaming
    @POST("api/llm-application/open/v2/model-api/{id}/sse-invoke")
    @Headers({"Content-Type: application/json"})
    Call<AssistantCompletion> sseInvoke(@Path("id") String id);

    /**
     * 3.8 推荐问题
     * @param appId 智能体（应用）id
     * @param conversationId 会话编号
     * @return ProblemsResponse
     */
    @GET("api/llm-application/open/history_session_record/{app_id}/{conversation_id}")
    @Headers({"Content-Type: application/json"})
    Call<ProblemsResponse> sessionRecord(@Path("app_id") String appId, @Path("conversation_id") String conversationId);
}
