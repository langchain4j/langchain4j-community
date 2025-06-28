package dev.langchain4j.community.model.xinference.client;

import static dev.langchain4j.community.model.xinference.client.utils.JsonUtil.getObjectMapper;

import dev.langchain4j.community.model.xinference.client.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.xinference.client.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.xinference.client.completion.CompletionRequest;
import dev.langchain4j.community.model.xinference.client.completion.CompletionResponse;
import dev.langchain4j.community.model.xinference.client.embedding.EmbeddingRequest;
import dev.langchain4j.community.model.xinference.client.embedding.EmbeddingResponse;
import dev.langchain4j.community.model.xinference.client.image.ImageRequest;
import dev.langchain4j.community.model.xinference.client.image.ImageResponse;
import dev.langchain4j.community.model.xinference.client.image.OcrRequest;
import dev.langchain4j.community.model.xinference.client.rerank.RerankRequest;
import dev.langchain4j.community.model.xinference.client.rerank.RerankResponse;
import dev.langchain4j.community.model.xinference.client.shared.StreamOptions;
import dev.langchain4j.internal.Utils;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import okhttp3.Cache;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class XinferenceClient {
    private static final Logger log = LoggerFactory.getLogger(XinferenceClient.class);
    private final String baseUrl;
    private final OkHttpClient okHttpClient;
    private final XinferenceApi xinferenceApi;
    private final boolean logStreamingResponses;

    private XinferenceClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .callTimeout(builder.callTimeout)
                .connectTimeout(builder.connectTimeout)
                .readTimeout(builder.readTimeout)
                .writeTimeout(builder.writeTimeout);

        if (builder.apiKey != null) {
            okHttpClientBuilder.addInterceptor(new AuthorizationHeaderInjector(builder.apiKey));
        }
        Map<String, String> headers = new HashMap<>();
        if (builder.customHeaders != null) {
            headers.putAll(builder.customHeaders);
        }
        if (!headers.isEmpty()) {
            okHttpClientBuilder.addInterceptor(new GenericHeaderInjector(headers));
        }
        if (builder.proxy != null) {
            okHttpClientBuilder.proxy(builder.proxy);
        }
        if (builder.logRequests) {
            okHttpClientBuilder.addInterceptor(new RequestLoggingInterceptor());
        }
        if (builder.logResponses) {
            okHttpClientBuilder.addInterceptor(new ResponseLoggingInterceptor());
        }
        this.logStreamingResponses = builder.logStreamingResponses;
        this.okHttpClient = okHttpClientBuilder.build();
        Retrofit.Builder retrofitBuilder =
                new Retrofit.Builder().baseUrl(this.baseUrl).client(okHttpClient);
        retrofitBuilder.addConverterFactory(JacksonConverterFactory.create(getObjectMapper()));
        this.xinferenceApi = retrofitBuilder.build().create(XinferenceApi.class);
    }

    private static MultipartBody.Builder toMultipartBuilder(ImageRequest request) {
        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MediaType.get("multipart/form-data"))
                .addFormDataPart("model", request.getModel())
                .addFormDataPart("prompt", request.getPrompt())
                .addFormDataPart("response_format", request.getResponseFormat().getValue());
        if (Utils.isNotNullOrBlank(request.getNegativePrompt())) {
            builder.addFormDataPart("negative_prompt", request.getNegativePrompt());
        }
        if (Objects.nonNull(request.getN())) {
            builder.addFormDataPart("n", String.valueOf(request.getN()));
        }
        if (Utils.isNotNullOrBlank(request.getSize())) {
            builder.addFormDataPart("size", request.getSize());
        }
        if (Utils.isNotNullOrBlank(request.getKwargs())) {
            builder.addFormDataPart("kwargs", request.getKwargs());
        }
        return builder;
    }

    public static Builder builder() {
        return new Builder();
    }

    public void shutdown() {
        okHttpClient.dispatcher().executorService().shutdown();
        okHttpClient.connectionPool().evictAll();
        Cache cache = okHttpClient.cache();
        if (cache != null) {
            try {
                cache.close();
            } catch (IOException e) {
                log.error("Failed to close cache", e);
            }
        }
    }

    public SyncOrAsyncOrStreaming<CompletionResponse> completions(CompletionRequest request) {
        return new RequestExecutor<>(
                xinferenceApi.completions(
                        CompletionRequest.builder().from(request).stream(null).build()),
                r -> r,
                okHttpClient,
                formatUrl("v1/completions"),
                () -> CompletionRequest.builder().from(request).stream(true)
                        .streamOptions(StreamOptions.of(true))
                        .build(),
                CompletionResponse.class,
                r -> r,
                logStreamingResponses);
    }

    public SyncOrAsyncOrStreaming<ChatCompletionResponse> chatCompletions(ChatCompletionRequest request) {
        return new RequestExecutor<>(
                xinferenceApi.chatCompletions(ChatCompletionRequest.builder().from(request).stream(null)
                        .build()),
                r -> r,
                okHttpClient,
                formatUrl("v1/chat/completions"),
                () -> ChatCompletionRequest.builder().from(request).stream(true)
                        .streamOptions(StreamOptions.of(true))
                        .build(),
                ChatCompletionResponse.class,
                r -> r,
                logStreamingResponses);
    }

    public SyncOrAsync<EmbeddingResponse> embeddings(EmbeddingRequest request) {
        return new RequestExecutor<>(xinferenceApi.embeddings(request), r -> r);
    }

    public SyncOrAsync<RerankResponse> rerank(RerankRequest request) {
        return new RequestExecutor<>(xinferenceApi.rerank(request), r -> r);
    }

    public SyncOrAsync<ImageResponse> generations(ImageRequest request) {
        return new RequestExecutor<>(xinferenceApi.generations(request), r -> r);
    }

    public SyncOrAsync<ImageResponse> variations(ImageRequest request, byte[] image) {
        MultipartBody.Builder builder = toMultipartBuilder(request);
        builder.addFormDataPart("image", "image", RequestBody.create(image, MediaType.parse("image")));
        return new RequestExecutor<>(xinferenceApi.variations(builder.build()), r -> r);
    }

    public SyncOrAsync<ImageResponse> inpainting(ImageRequest request, byte[] image, byte[] maskImage) {
        MultipartBody.Builder builder = toMultipartBuilder(request);
        builder.addFormDataPart("image", "image", RequestBody.create(image, MediaType.parse("image")));
        builder.addFormDataPart("mask_image", "mask_image", RequestBody.create(maskImage, MediaType.parse("image")));
        return new RequestExecutor<>(xinferenceApi.inpainting(builder.build()), r -> r);
    }

    public SyncOrAsync<String> ocr(OcrRequest request) {
        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MediaType.get("multipart/form-data"))
                .addFormDataPart("model", request.getModel())
                .addFormDataPart("image", "image", RequestBody.create(request.getImage(), MediaType.parse("image")));
        if (Utils.isNotNullOrBlank(request.getKwargs())) {
            builder.addFormDataPart("kwargs", request.getKwargs());
        }
        return new RequestExecutor<>(xinferenceApi.ocr(builder.build()), r -> r);
    }

    private String formatUrl(String endpoint) {
        return this.baseUrl + endpoint;
    }

    public static class Builder {

        private String baseUrl;
        private String apiKey;
        private Duration callTimeout = Duration.ofSeconds(60);
        private Duration connectTimeout = Duration.ofSeconds(60);
        private Duration readTimeout = Duration.ofSeconds(60);
        private Duration writeTimeout = Duration.ofSeconds(60);
        private Proxy proxy;
        private boolean logRequests;
        private boolean logResponses;
        private boolean logStreamingResponses;
        private Map<String, String> customHeaders;

        public Builder baseUrl(String baseUrl) {
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("baseUrl cannot be null or empty");
            }
            this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder callTimeout(Duration callTimeout) {
            if (callTimeout == null) {
                throw new IllegalArgumentException("callTimeout cannot be null");
            } else {
                this.callTimeout = callTimeout;
                return this;
            }
        }

        public Builder connectTimeout(Duration connectTimeout) {
            if (connectTimeout == null) {
                throw new IllegalArgumentException("connectTimeout cannot be null");
            } else {
                this.connectTimeout = connectTimeout;
                return this;
            }
        }

        public Builder readTimeout(Duration readTimeout) {
            if (readTimeout == null) {
                throw new IllegalArgumentException("readTimeout cannot be null");
            } else {
                this.readTimeout = readTimeout;
                return this;
            }
        }

        public Builder writeTimeout(Duration writeTimeout) {
            if (writeTimeout == null) {
                throw new IllegalArgumentException("writeTimeout cannot be null");
            } else {
                this.writeTimeout = writeTimeout;
                return this;
            }
        }

        public Builder proxy(Proxy.Type type, String ip, int port) {
            this.proxy = new Proxy(type, new InetSocketAddress(ip, port));
            return this;
        }

        public Builder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public Builder logRequests() {
            return this.logRequests(true);
        }

        public Builder logRequests(Boolean logRequests) {
            if (logRequests == null) {
                logRequests = false;
            }
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses() {
            return this.logResponses(true);
        }

        public Builder logResponses(Boolean logResponses) {
            if (logResponses == null) {
                logResponses = false;
            }
            this.logResponses = logResponses;
            return this;
        }

        public Builder logStreamingResponses() {
            return logStreamingResponses(true);
        }

        public Builder logStreamingResponses(Boolean logStreamingResponses) {
            if (logStreamingResponses == null) {
                logStreamingResponses = false;
            }
            this.logStreamingResponses = logStreamingResponses;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public XinferenceClient build() {
            return new XinferenceClient(this);
        }
    }
}
