package dev.langchain4j.community.store.embedding.vearch;

import static dev.langchain4j.community.store.embedding.vearch.VearchJsonUtils.fromJson;
import static dev.langchain4j.community.store.embedding.vearch.VearchJsonUtils.toJson;
import static dev.langchain4j.http.client.HttpMethod.DELETE;
import static dev.langchain4j.http.client.HttpMethod.GET;
import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Utils.copyIfNotNull;
import static dev.langchain4j.internal.Utils.ensureTrailingForwardSlash;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static java.time.Duration.ofSeconds;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.log.LoggingHttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

class VearchClient {

    private static final int HTTP_STATUS_OK = 0;

    private final HttpClient httpClient;
    private final String baseUrl;
    private final Map<String, String> defaultHeaders;

    public VearchClient(Builder builder) {
        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);

        HttpClient httpClient = httpClientBuilder
                .connectTimeout(
                        getOrDefault(getOrDefault(builder.timeout, httpClientBuilder.connectTimeout()), ofSeconds(15)))
                .readTimeout(
                        getOrDefault(getOrDefault(builder.timeout, httpClientBuilder.readTimeout()), ofSeconds(60)))
                .build();

        if (builder.logRequests != null || builder.logResponses != null) {
            this.httpClient = new LoggingHttpClient(httpClient, builder.logRequests, builder.logResponses);
        } else {
            this.httpClient = httpClient;
        }

        this.baseUrl = ensureTrailingForwardSlash(ensureNotBlank(builder.baseUrl, "baseUrl"));
        this.defaultHeaders = copyIfNotNull(builder.customHeaders);
    }

    public List<ListDatabaseResponse> listDatabase() {

        HttpRequest httpRequest = HttpRequest.builder()
                .method(GET)
                .url(baseUrl, "dbs")
                .addHeader("Content-Type", "application/json")
                .addHeaders(defaultHeaders)
                .build();

        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);

        TypeReference<ResponseWrapper<List<ListDatabaseResponse>>> typeReference = new TypeReference<>() {};
        ResponseWrapper<List<ListDatabaseResponse>> response = fromJson(successfulHttpResponse.body(), typeReference);
        if (response.getCode() != HTTP_STATUS_OK) {
            throw toException(response);
        }

        return response.getData();
    }

    public CreateDatabaseResponse createDatabase(String databaseName) {

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, String.format("dbs/%s", databaseName))
                .addHeader("Content-Type", "application/json")
                .addHeaders(defaultHeaders)
                .build();

        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);

        TypeReference<ResponseWrapper<CreateDatabaseResponse>> typeReference = new TypeReference<>() {};
        ResponseWrapper<CreateDatabaseResponse> response = fromJson(successfulHttpResponse.body(), typeReference);
        if (response.getCode() != HTTP_STATUS_OK) {
            throw toException(response);
        }

        return response.getData();
    }

    public List<ListSpaceResponse> listSpaceOfDatabase(String dbName) {

        HttpRequest httpRequest = HttpRequest.builder()
                .method(GET)
                .url(baseUrl, String.format("dbs/%s/spaces", dbName))
                .addHeader("Content-Type", "application/json")
                .addHeaders(defaultHeaders)
                .build();

        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);

        TypeReference<ResponseWrapper<List<ListSpaceResponse>>> typeReference = new TypeReference<>() {};
        ResponseWrapper<List<ListSpaceResponse>> response = fromJson(successfulHttpResponse.body(), typeReference);
        if (response.getCode() != HTTP_STATUS_OK) {
            throw toException(response);
        }

        return response.getData();
    }

    public CreateSpaceResponse createSpace(String dbName, CreateSpaceRequest request) {

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, String.format("dbs/%s/spaces", dbName))
                .body(toJson(request))
                .addHeader("Content-Type", "application/json")
                .addHeaders(defaultHeaders)
                .build();

        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);

        TypeReference<ResponseWrapper<CreateSpaceResponse>> typeReference = new TypeReference<>() {};
        ResponseWrapper<CreateSpaceResponse> response = fromJson(successfulHttpResponse.body(), typeReference);
        if (response.getCode() != HTTP_STATUS_OK) {
            throw toException(response);
        }

        return response.getData();
    }

    public void upsert(UpsertRequest request) {

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "document/upsert")
                .body(toJson(request))
                .addHeader("Content-Type", "application/json")
                .addHeaders(defaultHeaders)
                .build();

        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);

        TypeReference<ResponseWrapper<UpsertResponse>> typeReference = new TypeReference<>() {};
        ResponseWrapper<UpsertResponse> response = fromJson(successfulHttpResponse.body(), typeReference);
        if (response.getCode() != HTTP_STATUS_OK) {
            throw toException(response);
        }
    }

    public SearchResponse search(SearchRequest request) {

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "document/search")
                .body(toJson(request))
                .addHeader("Content-Type", "application/json")
                .addHeaders(defaultHeaders)
                .build();

        SuccessfulHttpResponse successfulHttpResponse = httpClient.execute(httpRequest);

        TypeReference<ResponseWrapper<SearchResponse>> typeReference = new TypeReference<>() {};
        ResponseWrapper<SearchResponse> response = fromJson(successfulHttpResponse.body(), typeReference);
        if (response.getCode() != HTTP_STATUS_OK) {
            throw toException(response);
        }

        return response.getData();
    }

    public void deleteSpace(String databaseName, String spaceName) {
        HttpRequest httpRequest = HttpRequest.builder()
                .method(DELETE)
                .url(baseUrl, String.format("dbs/%s/spaces/%s", databaseName, spaceName))
                .addHeaders(defaultHeaders)
                .build();

        httpClient.execute(httpRequest);
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {

        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private Duration timeout;
        private Boolean logRequests;
        private Boolean logResponses;
        private Map<String, String> customHeaders;

        Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        Builder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        Builder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        VearchClient build() {
            return new VearchClient(this);
        }
    }

    private RuntimeException toException(ResponseWrapper<?> responseWrapper) {
        return toException(responseWrapper.getCode(), responseWrapper.getMsg());
    }

    private RuntimeException toException(int code, String msg) {
        String errorMessage = String.format("code: %s; message: %s", code, msg);

        return new RuntimeException(errorMessage);
    }
}
