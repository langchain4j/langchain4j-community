package dev.langchain4j.community.model.xinference;

import dev.langchain4j.community.model.xinference.client.XinferenceClient;
import dev.langchain4j.community.model.xinference.client.completion.CompletionChoice;
import dev.langchain4j.community.model.xinference.client.completion.CompletionRequest;
import dev.langchain4j.community.model.xinference.client.completion.CompletionResponse;
import dev.langchain4j.community.model.xinference.spi.XinferenceLanguageModelBuilderFactory;
import dev.langchain4j.internal.Utils;
import dev.langchain4j.internal.ValidationUtils;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.output.Response;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.RetryUtils.withRetry;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class XinferenceLanguageModel implements LanguageModel {

    private final XinferenceClient client;
    private final String modelName;
    private final Integer maxTokens;
    private final Double temperature;
    private final Double topP;
    private final Integer n;
    private final Integer logprobs;
    private final Boolean echo;
    private final List<String> stop;
    private final Double presencePenalty;
    private final Double frequencyPenalty;
    private final String user;
    private final Integer maxRetries;

    public XinferenceLanguageModel(String baseUrl,
                                   String apiKey,
                                   String modelName,
                                   Integer maxTokens,
                                   Double temperature,
                                   Double topP,
                                   Integer n,
                                   Integer logprobs,
                                   Boolean echo,
                                   List<String> stop,
                                   Double presencePenalty,
                                   Double frequencyPenalty,
                                   String user,
                                   Integer maxRetries,
                                   Duration timeout,
                                   Proxy proxy,
                                   Boolean logRequests,
                                   Boolean logResponses,
                                   Map<String, String> customHeaders) {
        timeout = Utils.getOrDefault(timeout, Duration.ofSeconds(60));

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

        this.modelName = ValidationUtils.ensureNotBlank(modelName, "modelName");
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.topP = topP;
        this.n = n;
        this.logprobs = logprobs;
        this.echo = echo;
        this.stop = stop;
        this.presencePenalty = presencePenalty;
        this.frequencyPenalty = frequencyPenalty;
        this.user = user;
        this.maxRetries = Utils.getOrDefault(maxRetries, 3);
    }

    @Override
    public Response<String> generate(final String prompt) {
        final CompletionRequest request = CompletionRequest.builder()
                .model(modelName)
                .prompt(prompt)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .topP(topP)
                .n(n)
                .logprobs(logprobs)
                .echo(echo)
                .stop(stop)
                .presencePenalty(presencePenalty)
                .frequencyPenalty(frequencyPenalty)
                .user(user)
                .build();
        CompletionResponse response = withRetry(() -> client.completions(request).execute(), maxRetries);
        CompletionChoice completionChoice = response.getChoices().get(0);
        return Response.from(
                completionChoice.getText(),
                InternalXinferenceHelper.tokenUsageFrom(response.getUsage()),
                InternalXinferenceHelper.finishReasonFrom(completionChoice.getFinishReason())
        );
    }

    public static XinferenceLanguageModelBuilder builder() {
        for (XinferenceLanguageModelBuilderFactory factory : loadFactories(XinferenceLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new XinferenceLanguageModelBuilder();
    }

    public static class XinferenceLanguageModelBuilder {

        private String baseUrl;
        private String apiKey;
        private String modelName;
        private Integer maxTokens;
        private Double temperature;
        private Double topP;
        private Integer n;
        private Integer logprobs;
        private Boolean echo;
        private List<String> stop;
        private Double presencePenalty;
        private Double frequencyPenalty;
        private String user;
        private Integer maxRetries;
        private Duration timeout;
        private Proxy proxy;
        private Boolean logRequests;
        private Boolean logResponses;
        private Map<String, String> customHeaders;

        public XinferenceLanguageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public XinferenceLanguageModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public XinferenceLanguageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public XinferenceLanguageModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public XinferenceLanguageModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public XinferenceLanguageModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public XinferenceLanguageModelBuilder n(Integer n) {
            this.n = n;
            return this;
        }

        public XinferenceLanguageModelBuilder logprobs(Integer logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public XinferenceLanguageModelBuilder echo(Boolean echo) {
            this.echo = echo;
            return this;
        }

        public XinferenceLanguageModelBuilder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public XinferenceLanguageModelBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public XinferenceLanguageModelBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public XinferenceLanguageModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        public XinferenceLanguageModelBuilder maxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public XinferenceLanguageModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public XinferenceLanguageModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public XinferenceLanguageModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public XinferenceLanguageModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public XinferenceLanguageModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        // Build method to create the object
        public XinferenceLanguageModel build() {
            return new XinferenceLanguageModel(
                    baseUrl,
                    apiKey,
                    modelName,
                    maxTokens,
                    temperature,
                    topP,
                    n,
                    logprobs,
                    echo,
                    stop,
                    presencePenalty,
                    frequencyPenalty,
                    user,
                    maxRetries,
                    timeout,
                    proxy,
                    logRequests,
                    logResponses,
                    customHeaders
            );
        }
    }
}
