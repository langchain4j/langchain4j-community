package dev.langchain4j.community.model.minimax.client;

import static dev.langchain4j.community.model.minimax.client.utils.JsonUtil.getObjectMapper;

import dev.langchain4j.community.model.minimax.client.chat.ChatCompletionRequest;
import dev.langchain4j.community.model.minimax.client.chat.ChatCompletionResponse;
import dev.langchain4j.community.model.minimax.client.shared.StreamOptions;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class MiniMaxClient {

    private static final Logger log = LoggerFactory.getLogger(MiniMaxClient.class);

    private final String baseUrl;
    private final OkHttpClient okHttpClient;
    private final MiniMaxApi miniMaxApi;
    private final boolean logStreamingResponses;

    private MiniMaxClient(Builder builder) {
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
        this.miniMaxApi = retrofitBuilder.build().create(MiniMaxApi.class);
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

    public SyncOrAsyncOrStreaming<ChatCompletionResponse> chatCompletions(ChatCompletionRequest request) {
        return new RequestExecutor<>(
                miniMaxApi.chatCompletions(
                        ChatCompletionRequest.builder().from(request).stream(null).build()),
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
            }
            this.callTimeout = callTimeout;
            return this;
        }

        public Builder connectTimeout(Duration connectTimeout) {
            if (connectTimeout == null) {
                throw new IllegalArgumentException("connectTimeout cannot be null");
            }
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder readTimeout(Duration readTimeout) {
            if (readTimeout == null) {
                throw new IllegalArgumentException("readTimeout cannot be null");
            }
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder writeTimeout(Duration writeTimeout) {
            if (writeTimeout == null) {
                throw new IllegalArgumentException("writeTimeout cannot be null");
            }
            this.writeTimeout = writeTimeout;
            return this;
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

        public MiniMaxClient build() {
            return new MiniMaxClient(this);
        }
    }
}
