package dev.langchain4j.community.model.typesafe;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Json.fromJson;
import static dev.langchain4j.internal.Json.toJson;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import dev.langchain4j.Experimental;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpClientBuilderLoader;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.structureddecision.ChoiceQuestion;
import dev.langchain4j.model.structureddecision.NoulCriteria;
import dev.langchain4j.model.structureddecision.NoulQuestion;
import dev.langchain4j.model.structureddecision.OptionCriteria;
import dev.langchain4j.model.structureddecision.Question;
import dev.langchain4j.model.structureddecision.ScoreQuestion;
import dev.langchain4j.model.structureddecision.StructuredDecisionAnswer;
import dev.langchain4j.model.structureddecision.StructuredDecisionModel;
import dev.langchain4j.model.structureddecision.StructuredDecisionRequest;
import dev.langchain4j.model.structureddecision.StructuredDecisionRequestParameters;
import dev.langchain4j.model.structureddecision.StructuredDecisionResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A {@link StructuredDecisionModel} backed by TypeSafe AI's System One API. */
@Experimental
public final class TypeSafeStructuredDecisionModel implements StructuredDecisionModel {

    /** Default TypeSafe API root used by the official SDKs. */
    public static final String DEFAULT_BASE_URL = "https://api.typesafe.ai";

    /** Default System One model used by the official SDKs. */
    public static final String DEFAULT_MODEL_NAME = "jev-latest";

    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final StructuredDecisionRequestParameters defaultRequestParameters;

    private TypeSafeStructuredDecisionModel(Builder builder) {
        this.apiKey = ensureNotBlank(builder.apiKey, "apiKey");
        this.baseUrl = withoutTrailingSlash(ensureNotBlank(getOrDefault(builder.baseUrl, DEFAULT_BASE_URL), "baseUrl"));
        String modelName = ensureNotBlank(getOrDefault(builder.modelName, DEFAULT_MODEL_NAME), "modelName");
        this.defaultRequestParameters = StructuredDecisionRequestParameters.builder()
                .modelName(modelName)
                .build();
        HttpClientBuilder httpClientBuilder =
                getOrDefault(builder.httpClientBuilder, HttpClientBuilderLoader::loadHttpClientBuilder);
        this.httpClient = httpClientBuilder.build();
    }

    /** Creates a builder for a TypeSafe-backed structured decision model. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public StructuredDecisionResponse decide(StructuredDecisionRequest request) {
        ensureNotNull(request, "request");
        StructuredDecisionRequestParameters parameters = defaultRequestParameters.overrideWith(request.parameters());
        SystemOneRequest systemOneRequest =
                new SystemOneRequest(request.state(), parameters.modelName(), mapQuestions(request.questions()));

        HttpRequest httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "/v1/systemone")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .body(toJson(systemOneRequest))
                .build();
        SuccessfulHttpResponse response = httpClient.execute(httpRequest);
        return mapResponse(fromJson(response.body(), SystemOneResponse.class));
    }

    @Override
    public StructuredDecisionRequestParameters defaultRequestParameters() {
        return defaultRequestParameters;
    }

    private static Map<String, SystemOneQuestion> mapQuestions(Map<String, Question> questions) {
        Map<String, SystemOneQuestion> result = new LinkedHashMap<>();
        questions.forEach((name, question) -> result.put(name, mapQuestion(question)));
        return result;
    }

    private static SystemOneQuestion mapQuestion(Question question) {
        if (question instanceof NoulQuestion noulQuestion) {
            Object criteria =
                    noulQuestion.criteria() == null ? null : Map.of("true", describe(noulQuestion.criteria()));
            return new SystemOneQuestion("noul", noulQuestion.instructions(), criteria);
        }
        if (question instanceof ChoiceQuestion choiceQuestion) {
            Map<String, Object> criteria = new LinkedHashMap<>();
            choiceQuestion.options().forEach((name, value) -> criteria.put(name, describe(value)));
            return new SystemOneQuestion("choice", choiceQuestion.instructions(), criteria);
        }
        if (question instanceof ScoreQuestion scoreQuestion) {
            List<Object> criteria = new ArrayList<>();
            scoreQuestion.levels().forEach((name, value) -> criteria.add(describe(name, value)));
            return new SystemOneQuestion("score", scoreQuestion.instructions(), criteria);
        }
        throw new IllegalArgumentException(
                "Unsupported question type: " + question.getClass().getName());
    }

    private static Map<String, Object> describe(NoulCriteria criteria) {
        Map<String, Object> description = new LinkedHashMap<>();
        putIfPresent(description, "what", criteria.what());
        putIfPresent(description, "not_for", criteria.notFor());
        putIfNotEmpty(description, "examples", criteria.examples());
        return description;
    }

    private static Map<String, Object> describe(OptionCriteria criteria) {
        Map<String, Object> description = new LinkedHashMap<>();
        putIfPresent(description, "what", criteria.what());
        putIfPresent(description, "not_for", criteria.notFor());
        putIfNotEmpty(description, "examples", criteria.examples());
        return description;
    }

    private static Map<String, Object> describe(String level, OptionCriteria criteria) {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("level", level);
        description.putAll(describe(criteria));
        return description;
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static void putIfNotEmpty(Map<String, Object> target, String key, List<String> value) {
        if (!value.isEmpty()) {
            target.put(key, value);
        }
    }

    private static StructuredDecisionResponse mapResponse(SystemOneResponse response) {
        StructuredDecisionResponse.Builder result = StructuredDecisionResponse.builder();
        response.answers().forEach((name, answer) -> result.answer(name, mapAnswer(answer)));
        return result.build();
    }

    private static StructuredDecisionAnswer mapAnswer(SystemOneAnswer answer) {
        StructuredDecisionAnswer.Builder result = StructuredDecisionAnswer.builder();
        switch (answer.type()) {
            case "noul" -> result.noul(answer.noul());
            case "choice" -> result.choice(answer.choice()).confidence(answer.confidence());
            case "score" -> result.score(answer.score()).confidence(answer.confidence());
            default -> throw new IllegalArgumentException("Unsupported System One answer type: " + answer.type());
        }
        return result.build();
    }

    private static String withoutTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    /** Builder for {@link TypeSafeStructuredDecisionModel}. */
    public static final class Builder {
        private String apiKey;
        private String baseUrl;
        private String modelName;
        private HttpClientBuilder httpClientBuilder;

        /** Sets the TypeSafe API key. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** Sets the TypeSafe API root. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** Sets the default model name. */
        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        /** Sets the HTTP client builder used to create the transport. */
        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        /** Builds the model. */
        public TypeSafeStructuredDecisionModel build() {
            return new TypeSafeStructuredDecisionModel(this);
        }
    }
}
