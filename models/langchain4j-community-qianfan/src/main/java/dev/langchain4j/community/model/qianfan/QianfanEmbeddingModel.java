package dev.langchain4j.community.model.qianfan;

import static dev.langchain4j.community.model.qianfan.InternalQianfanHelper.tokenUsageFrom;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;
import static java.util.stream.Collectors.toList;

import dev.langchain4j.community.model.qianfan.client.QianfanClient;
import dev.langchain4j.community.model.qianfan.client.embedding.EmbeddingRequest;
import dev.langchain4j.community.model.qianfan.client.embedding.EmbeddingResponse;
import dev.langchain4j.community.model.qianfan.spi.QianfanEmbeddingModelBuilderFactory;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.net.Proxy;
import java.util.List;

/**
 * see details here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu
 */
public class QianfanEmbeddingModel extends DimensionAwareEmbeddingModel {

    private final QianfanClient client;
    private final String baseUrl;
    private final String modelName;
    private final Integer maxRetries;
    private final String user;
    private final String endpoint;

    public QianfanEmbeddingModel(
            String baseUrl,
            String apiKey,
            String secretKey,
            Integer maxRetries,
            String modelName,
            String endpoint,
            String user,
            Boolean logRequests,
            Boolean logResponses,
            Proxy proxy) {
        if (Utils.isNullOrBlank(apiKey) || Utils.isNullOrBlank(secretKey)) {
            throw new IllegalArgumentException(
                    " api key and secret key must be defined. It can be generated here: https://console.bce.baidu.com/qianfan/ais/console/applicationConsole/application");
        }

        this.modelName = modelName;
        this.endpoint =
                Utils.isNullOrBlank(endpoint) ? QianfanEmbeddingModelNameEnum.fromModelName(modelName) : endpoint;

        if (Utils.isNullOrBlank(this.endpoint)) {
            throw new IllegalArgumentException(
                    "Qianfan is no such model name. You can see model name here: https://cloud.baidu.com/doc/WENXINWORKSHOP/s/Nlks5zkzu");
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
        this.maxRetries = getOrDefault(maxRetries, 3);
        this.user = user;
    }

    public static QianfanEmbeddingModelBuilder builder() {
        for (QianfanEmbeddingModelBuilderFactory factory : loadFactories(QianfanEmbeddingModelBuilderFactory.class)) {
            return factory.get();
        }
        return new QianfanEmbeddingModelBuilder();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {

        List<String> texts = textSegments.stream().map(TextSegment::text).collect(toList());

        return embedTexts(texts);
    }

    private Response<List<Embedding>> embedTexts(List<String> texts) {

        EmbeddingRequest request = EmbeddingRequest.builder()
                .input(texts)
                .model(modelName)
                .user(user)
                .build();

        EmbeddingResponse response =
                withRetry(() -> client.embedding(request, endpoint).execute(), maxRetries);

        List<Embedding> embeddings = response.getData().stream()
                .map(embedding -> Embedding.from(embedding.getEmbedding()))
                .collect(toList());

        return Response.from(embeddings, tokenUsageFrom(response));
    }

    public static class QianfanEmbeddingModelBuilder {

        private String baseUrl;
        private String apiKey;
        private String secretKey;
        private Integer maxRetries;
        private String modelName;
        private String endpoint;
        private String user;
        private Boolean logRequests;
        private Boolean logResponses;
        private Proxy proxy;

        public QianfanEmbeddingModelBuilder() {
            // This is public so it can be extended
            // By default with Lombok it becomes package private
        }

        public QianfanEmbeddingModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public QianfanEmbeddingModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public QianfanEmbeddingModelBuilder secretKey(String secretKey) {
            this.secretKey = secretKey;
            return this;
        }

        public QianfanEmbeddingModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public QianfanEmbeddingModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public QianfanEmbeddingModelBuilder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        public QianfanEmbeddingModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        public QianfanEmbeddingModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public QianfanEmbeddingModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public QianfanEmbeddingModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public QianfanEmbeddingModel build() {
            return new QianfanEmbeddingModel(
                    baseUrl,
                    apiKey,
                    secretKey,
                    maxRetries,
                    modelName,
                    endpoint,
                    user,
                    logRequests,
                    logResponses,
                    proxy);
        }
    }
}
