package dev.langchain4j.community.model.xinference;

import static dev.langchain4j.community.model.xinference.ImageUtils.imageByteArray;
import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

import dev.langchain4j.community.model.xinference.client.XinferenceClient;
import dev.langchain4j.community.model.xinference.client.image.ImageData;
import dev.langchain4j.community.model.xinference.client.image.ImageRequest;
import dev.langchain4j.community.model.xinference.client.image.ImageResponse;
import dev.langchain4j.community.model.xinference.client.image.ResponseFormat;
import dev.langchain4j.community.model.xinference.spi.XinferenceImageModelBuilderFactory;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class XinferenceImageModel implements ImageModel {
    private final XinferenceClient client;
    private final String modelName;
    private final String negativePrompt;
    private final ResponseFormat responseFormat;
    private final String size;
    private final String kwargs;
    private final String user;
    private final Integer maxRetries;

    public XinferenceImageModel(
            String baseUrl,
            String apiKey,
            String modelName,
            String negativePrompt,
            ResponseFormat responseFormat,
            String size,
            String kwargs,
            String user,
            Integer maxRetries,
            Duration timeout,
            Proxy proxy,
            Boolean logRequests,
            Boolean logResponses,
            Map<String, String> customHeaders) {
        timeout = getOrDefault(timeout, Duration.ofSeconds(60));
        this.client = XinferenceClient.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .callTimeout(timeout)
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .proxy(proxy)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .customHeaders(customHeaders)
                .build();
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.negativePrompt = negativePrompt;
        this.responseFormat = getOrDefault(responseFormat, ResponseFormat.B64_JSON);
        this.size = getOrDefault(size, "256x256");
        this.kwargs = kwargs;
        this.user = user;
        this.maxRetries = getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<Image> generate(final String prompt) {
        final ImageRequest request = ImageRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .negativePrompt(negativePrompt)
                .responseFormat(responseFormat)
                .size(size)
                .kwargs(kwargs)
                .user(user)
                .build();
        ImageResponse response =
                withRetry(() -> client.generations(request), maxRetries).execute();
        return Response.from(fromImageData(response.getData().get(0)));
    }

    @Override
    public Response<List<Image>> generate(final String prompt, final int total) {
        final ImageRequest request = ImageRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .negativePrompt(negativePrompt)
                .n(total)
                .responseFormat(responseFormat)
                .size(size)
                .kwargs(kwargs)
                .user(user)
                .build();
        ImageResponse response =
                withRetry(() -> client.generations(request), maxRetries).execute();
        final List<Image> list = response.getData().stream()
                .map(XinferenceImageModel::fromImageData)
                .toList();
        return Response.from(list);
    }

    @Override
    public Response<Image> edit(final Image image, final String prompt) {
        final ImageRequest request = ImageRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .negativePrompt(negativePrompt)
                .responseFormat(responseFormat)
                .size(size)
                .kwargs(kwargs)
                .user(user)
                .build();
        ImageResponse response = withRetry(() -> client.variations(request, imageByteArray(image)), maxRetries)
                .execute();
        return Response.from(fromImageData(response.getData().get(0)));
    }

    @Override
    public Response<Image> edit(final Image image, final Image mask, final String prompt) {
        final ImageRequest request = ImageRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .negativePrompt(negativePrompt)
                .responseFormat(responseFormat)
                .size(size)
                .kwargs(kwargs)
                .user(user)
                .build();
        ImageResponse response = withRetry(
                        () -> client.inpainting(request, imageByteArray(image), imageByteArray(mask)), maxRetries)
                .execute();
        return Response.from(fromImageData(response.getData().get(0)));
    }

    private static Image fromImageData(ImageData data) {
        final Image.Builder builder = Image.builder().base64Data(data.getB64Json());
        if (isNotNullOrBlank(data.getUrl())) {
            builder.url(data.getUrl());
        }
        return builder.build();
    }

    public static XinferenceImageModelBuilder builder() {
        for (XinferenceImageModelBuilderFactory factory : loadFactories(XinferenceImageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new XinferenceImageModelBuilder();
    }

    public static class XinferenceImageModelBuilder {
        private String baseUrl;
        private String apiKey;
        private String modelName;
        private String negativePrompt;
        private ResponseFormat responseFormat;
        private String size;
        private String kwargs;
        private String user;
        private Integer maxRetries;
        private Duration timeout;
        private Proxy proxy;
        private Boolean logRequests;
        private Boolean logResponses;
        private Map<String, String> customHeaders;

        public XinferenceImageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public XinferenceImageModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public XinferenceImageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public XinferenceImageModelBuilder negativePrompt(String negativePrompt) {
            this.negativePrompt = negativePrompt;
            return this;
        }

        public XinferenceImageModelBuilder responseFormat(ResponseFormat responseFormat) {
            this.responseFormat = responseFormat;
            return this;
        }

        public XinferenceImageModelBuilder size(String size) {
            this.size = size;
            return this;
        }

        public XinferenceImageModelBuilder kwargs(String kwargs) {
            this.kwargs = kwargs;
            return this;
        }

        public XinferenceImageModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        public XinferenceImageModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public XinferenceImageModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public XinferenceImageModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public XinferenceImageModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public XinferenceImageModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public XinferenceImageModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        // Build method to create the actual XinferenceImageModel instance
        public XinferenceImageModel build() {
            return new XinferenceImageModel(
                    this.baseUrl,
                    this.apiKey,
                    this.modelName,
                    this.negativePrompt,
                    this.responseFormat,
                    this.size,
                    this.kwargs,
                    this.user,
                    this.maxRetries,
                    this.timeout,
                    this.proxy,
                    this.logRequests,
                    this.logResponses,
                    this.customHeaders);
        }
    }
}
