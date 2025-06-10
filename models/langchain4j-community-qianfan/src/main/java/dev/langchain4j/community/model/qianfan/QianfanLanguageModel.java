package dev.langchain4j.community.model.qianfan;

import dev.langchain4j.community.model.qianfan.client.QianfanClient;
import dev.langchain4j.community.model.qianfan.client.completion.CompletionRequest;
import dev.langchain4j.community.model.qianfan.client.completion.CompletionResponse;
import dev.langchain4j.community.model.qianfan.spi.QianfanLanguageModelBuilderFactory;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;

import java.net.Proxy;

import static dev.langchain4j.community.model.qianfan.InternalQianfanHelper.finishReasonFrom;
import static dev.langchain4j.community.model.qianfan.InternalQianfanHelper.tokenUsageFrom;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

/**
 * see details here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu
 */
public class QianfanLanguageModel implements LanguageModel {

    private final QianfanClient client;
    private final String baseUrl;
    private final Double temperature;
    private final Double topP;
    private final String modelName;
    private final Double penaltyScore;
    private final Integer maxRetries;
    private final Integer topK;
    private final String endpoint;

    public QianfanLanguageModel(String baseUrl,
                                String apiKey,
                                String secretKey,
                                Double temperature,
                                Integer maxRetries,
                                Integer topK,
                                Double topP,
                                String modelName,
                                String endpoint,
                                Double penaltyScore,
                                Boolean logRequests,
                                Boolean logResponses,
                                Proxy proxy
    ) {
        if (Utils.isNullOrBlank(apiKey) || Utils.isNullOrBlank(secretKey)) {
            throw new IllegalArgumentException(" api key and secret key must be defined. It can be generated here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application");
        }

        this.modelName = modelName;
        this.endpoint = Utils.isNullOrBlank(endpoint) ? QianfanLanguageModelNameEnum.fromModelName(modelName) : endpoint;

        if (Utils.isNullOrBlank(this.endpoint)) {
            throw new IllegalArgumentException("Qianfan is no such model name. You can see model name here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu");
        }

        this.baseUrl = getOrDefault(baseUrl, "https://aip.baidubce.com");

        this.client = QianfanClient.builder()
                .baseUrl(this.baseUrl)
                .apiKey(apiKey)
                .secretKey(secretKey)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .proxy(proxy)
                .build();
        this.temperature = temperature;
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.topP = topP;
        this.topK = topK;
        this.penaltyScore = penaltyScore;
    }

    @Override
    public Response<String> generate(String prompt) {

        CompletionRequest request = CompletionRequest.builder()
                .prompt(prompt)
                .topK(topK)
                .topP(topP)
                .temperature(temperature)
                .penaltyScore(penaltyScore)
                .build();


        CompletionResponse response = withRetry(() -> client.completion(request, false, endpoint).execute(), maxRetries);

        return Response.from(
                response.getResult(),
                tokenUsageFrom(response),
                finishReasonFrom(response.getFinishReason())
        );
    }

    public static QianfanLanguageModelBuilder builder() {
        for (QianfanLanguageModelBuilderFactory factory : loadFactories(QianfanLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new QianfanLanguageModelBuilder();
    }

    public static class QianfanLanguageModelBuilder {

        private String baseUrl;
        private String apiKey;
        private String secretKey;
        private Double temperature;
        private Integer maxRetries;
        private Integer topK;
        private Double topP;
        private String modelName;
        private String endpoint;
        private Double penaltyScore;
        private Boolean logRequests;
        private Boolean logResponses;
        private Proxy proxy;

        public QianfanLanguageModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public QianfanLanguageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public QianfanLanguageModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public QianfanLanguageModelBuilder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        public QianfanLanguageModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public QianfanLanguageModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public QianfanLanguageModelBuilder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public QianfanLanguageModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public QianfanLanguageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public QianfanLanguageModelBuilder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public QianfanLanguageModelBuilder penaltyScore(Double penaltyScore) {
            this.penaltyScore = penaltyScore;
            return this;
        }

        public QianfanLanguageModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public QianfanLanguageModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public QianfanLanguageModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public QianfanLanguageModel build() {
            return new QianfanLanguageModel(
                    baseUrl,
                    apiKey,
                    secretKey,
                    temperature,
                    maxRetries,
                    topK,
                    topP,
                    modelName,
                    endpoint,
                    penaltyScore,
                    logRequests,
                    logResponses,
                    proxy
            );
        }
    }
}
