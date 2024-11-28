package dev.langchain4j.community.model.xinference;

import dev.langchain4j.community.model.xinference.client.XinferenceClient;
import dev.langchain4j.community.model.xinference.client.completion.CompletionChoice;
import dev.langchain4j.community.model.xinference.client.completion.CompletionRequest;
import dev.langchain4j.community.model.xinference.client.shared.StreamOptions;
import dev.langchain4j.community.model.xinference.spi.XinferenceStreamingLanguageModelBuilderFactory;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.language.StreamingLanguageModel;
import dev.langchain4j.model.output.Response;

import java.net.Proxy;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.spi.ServiceHelper.loadFactories;

public class XinferenceStreamingLanguageModel implements StreamingLanguageModel {

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

    public XinferenceStreamingLanguageModel(String baseUrl,
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
                .logStreamingResponses(logResponses)
                .customHeaders(customHeaders)
                .build();

        this.modelName = ensureNotBlank(modelName, "modelName");
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
    }

    @Override
    public void generate(final String prompt, final StreamingResponseHandler<String> handler) {
        final CompletionRequest request = CompletionRequest.builder()
                .stream(true)
                .streamOptions(StreamOptions.of(true))
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
        final XinferenceStreamingResponseBuilder responseBuilder = new XinferenceStreamingResponseBuilder();
        client.completions(request)
                .onPartialResponse(completionResponse -> {
                    responseBuilder.append(completionResponse);
                    for (final CompletionChoice choice : completionResponse.getChoices()) {
                        final String text = choice.getText();
                        if (isNotNullOrEmpty(text)) {
                            handler.onNext(text);
                        }
                    }
                })
                .onComplete(() -> {
                    Response<AiMessage> response = responseBuilder.build();
                    handler.onComplete(Response.from(
                            response.content().text(),
                            response.tokenUsage(),
                            response.finishReason()
                    ));
                })
                .onError(handler::onError)
                .execute();
    }

    public static XinferenceStreamingLanguageModelBuilder builder() {
        for (XinferenceStreamingLanguageModelBuilderFactory factory : loadFactories(XinferenceStreamingLanguageModelBuilderFactory.class)) {
            return factory.get();
        }
        return new XinferenceStreamingLanguageModelBuilder();
    }


    public static class XinferenceStreamingLanguageModelBuilder {

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
        private Duration timeout;
        private Proxy proxy;
        private Boolean logRequests;
        private Boolean logResponses;
        private Map<String, String> customHeaders;

        public XinferenceStreamingLanguageModelBuilder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public XinferenceStreamingLanguageModelBuilder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public XinferenceStreamingLanguageModelBuilder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public XinferenceStreamingLanguageModelBuilder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public XinferenceStreamingLanguageModelBuilder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public XinferenceStreamingLanguageModelBuilder topP(Double topP) {
            this.topP = topP;
            return this;
        }

        public XinferenceStreamingLanguageModelBuilder n(Integer n) {
            this.n = n;
            return this;
        }

        public XinferenceStreamingLanguageModelBuilder logprobs(Integer logprobs) {
            this.logprobs = logprobs;
            return this;
        }

        public XinferenceStreamingLanguageModelBuilder echo(Boolean echo) {
            this.echo = echo;
            return this;
        }

        public XinferenceStreamingLanguageModelBuilder stop(List<String> stop) {
            this.stop = stop;
            return this;
        }

        public XinferenceStreamingLanguageModelBuilder presencePenalty(Double presencePenalty) {
            this.presencePenalty = presencePenalty;
            return this;
        }

        public XinferenceStreamingLanguageModelBuilder frequencyPenalty(Double frequencyPenalty) {
            this.frequencyPenalty = frequencyPenalty;
            return this;
        }

        public XinferenceStreamingLanguageModelBuilder user(String user) {
            this.user = user;
            return this;
        }

        public XinferenceStreamingLanguageModelBuilder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public XinferenceStreamingLanguageModelBuilder proxy(Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        public XinferenceStreamingLanguageModelBuilder logRequests(Boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public XinferenceStreamingLanguageModelBuilder logResponses(Boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public XinferenceStreamingLanguageModelBuilder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public XinferenceStreamingLanguageModel build() {
            return new XinferenceStreamingLanguageModel(
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
                    timeout,
                    proxy,
                    logRequests,
                    logResponses,
                    customHeaders
            );
        }
    }
}
