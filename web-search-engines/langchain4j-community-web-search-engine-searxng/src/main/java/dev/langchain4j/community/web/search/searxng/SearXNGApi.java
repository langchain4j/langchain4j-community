package dev.langchain4j.community.web.search.searxng;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.QueryMap;

import java.util.Map;

interface SearXNGApi {

    @GET("search")
    @Headers({"Content-Type: application/json"})
    Call<SearXNGResponse> search(@QueryMap Map<String, Object> params);
}
