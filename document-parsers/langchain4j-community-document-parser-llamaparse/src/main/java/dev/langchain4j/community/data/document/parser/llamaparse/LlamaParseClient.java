package dev.langchain4j.community.data.document.parser.llamaparse;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static java.time.Duration.ofSeconds;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class LlamaParseClient {
    private static final Logger log = LoggerFactory.getLogger(LlamaParseClient.class);

    private final LlamaParseApi llamaParseApi;

    public LlamaParseClient(String apiKey) {
        llamaParseApi = createLlamaParseClient(null, null, apiKey);
    }

    public LlamaParseClient(String baseUrl, Duration timeout, String apiKey) {
        llamaParseApi = createLlamaParseClient(baseUrl, timeout, apiKey);
    }

    @NonNull
    private LlamaParseApi createLlamaParseClient(String baseUrl, Duration timeout, String apiKey) {
        final LlamaParseApi llamaParseApi;
        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
                .addInterceptor(new ApiKeyInsertingInterceptor(apiKey))
                .callTimeout(getOrDefault(timeout, ofSeconds(30)))
                .connectTimeout(getOrDefault(timeout, ofSeconds(30)))
                .readTimeout(getOrDefault(timeout, ofSeconds(30)))
                .writeTimeout(getOrDefault(timeout, ofSeconds(30)));

        OkHttpClient okHttpClient = okHttpClientBuilder.build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(getOrDefault(baseUrl, "https://api.cloud.llamaindex.ai/api/parsing/"))
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create())
                .build();

        llamaParseApi = retrofit.create(LlamaParseApi.class);
        return llamaParseApi;
    }

    public LlamaParseResponse upload(Path path, String parsingInstructions) {
        try {
            RequestBody requestFile = RequestBody.create(MediaType.parse("application/pdf"), path.toFile());

            MultipartBody.Part filePart =
                    MultipartBody.Part.createFormData("file", path.toFile().getName(), requestFile);

            MultipartBody.Part parsingInstructionsPart =
                    MultipartBody.Part.createFormData("parsingInstructions", parsingInstructions);

            retrofit2.Response<LlamaParseResponse> retrofitResponse =
                    llamaParseApi.upload(filePart, parsingInstructionsPart).execute();

            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public ResponseBody jsonResult(String jobId) {
        try {
            retrofit2.Response<ResponseBody> response =
                    llamaParseApi.jsonResult(jobId).execute();
            if (response.isSuccessful()) {
                return response.body();
            } else {
                throw toException(response);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public LlamaParseMarkdownResponse markdownResult(String jobId) {
        try {
            retrofit2.Response<LlamaParseMarkdownResponse> response =
                    llamaParseApi.markdownResult(jobId).execute();
            if (response != null && response.isSuccessful()) {
                return response.body();
            } else {
                throw toException(response);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public LlamaParseTextResponse textResult(String jobId) {
        try {
            retrofit2.Response<LlamaParseTextResponse> response =
                    llamaParseApi.textResult(jobId).execute();
            if (response.isSuccessful()) {
                return response.body();
            } else {
                throw toException(response);
            }
        } catch (Exception e) {
            log.error("Error getting text result from the job {}", jobId);
            throw new RuntimeException(e);
        }
    }

    public ResponseBody imageResult(String jobId, String imageName) {
        try {
            retrofit2.Response<ResponseBody> response =
                    llamaParseApi.imageResult(jobId, imageName).execute();
            if (response.isSuccessful()) {
                return response.body();
            } else {
                throw toException(response);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public LlamaParseResponse jobStatus(String jobId) {
        try {
            retrofit2.Response<LlamaParseResponse> retrofitResponse =
                    llamaParseApi.jobStatus(jobId).execute();

            if (retrofitResponse.isSuccessful()) {
                return retrofitResponse.body();
            } else {
                throw toException(retrofitResponse);
            }

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private RuntimeException toException(retrofit2.Response<?> response) throws IOException {
        int code = response.code();
        String body = response.errorBody().string();

        String errorMessage = String.format("status code: %s; body: %s", code, body);
        return new RuntimeException(errorMessage);
    }
}
