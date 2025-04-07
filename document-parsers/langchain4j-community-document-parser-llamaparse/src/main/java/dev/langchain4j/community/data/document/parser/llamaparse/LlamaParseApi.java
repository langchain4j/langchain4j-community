package dev.langchain4j.community.data.document.parser.llamaparse;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;

interface LlamaParseApi {

    @POST("upload")
    @Multipart
    @Headers({"accept: application/json"})
    Call<LlamaParseResponse> upload(@Part MultipartBody.Part file, @Part MultipartBody.Part parsingInstructions);

    @GET("job/{jobId}/result/json")
    @Headers({"accept: application/json"})
    Call<ResponseBody> jsonResult(@Path("jobId") String jobId);

    @GET("job/{jobId}/result/markdown")
    @Headers({"accept: application/json"})
    Call<LlamaParseMarkdownResponse> markdownResult(@Path("jobId") String jobId);

    @GET("job/{jobId}/result/text")
    @Headers({"accept: application/json"})
    Call<LlamaParseTextResponse> textResult(@Path("jobId") String jobId);

    @GET("job/{jobId}/result/image/{image_name}")
    @Headers({"accept: application/json"})
    Call<ResponseBody> imageResult(@Path("jobId") String jobId, @Path("image_name") String image_name);

    @GET("job/{jobId}")
    @Headers({"accept: application/json"})
    Call<LlamaParseResponse> jobStatus(@Path("jobId") String jobId);
}
