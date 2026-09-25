package dev.langchain4j.community.model.systemone;

import static dev.langchain4j.http.client.HttpMethod.POST;
import static dev.langchain4j.internal.Json.fromJson;
import static dev.langchain4j.internal.Json.toJson;

import dev.langchain4j.exception.UnsupportedFeatureException;
import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.model.structureddecision.ChoiceAnswer;
import dev.langchain4j.model.structureddecision.ChoiceQuestion;
import dev.langchain4j.model.structureddecision.ConfidenceProvenance;
import dev.langchain4j.model.structureddecision.NoulAnswer;
import dev.langchain4j.model.structureddecision.NoulCriteria;
import dev.langchain4j.model.structureddecision.NoulQuestion;
import dev.langchain4j.model.structureddecision.OptionCriteria;
import dev.langchain4j.model.structureddecision.Question;
import dev.langchain4j.model.structureddecision.ScoreAnswer;
import dev.langchain4j.model.structureddecision.ScoreQuestion;
import dev.langchain4j.model.structureddecision.StructuredDecisionAnswer;
import dev.langchain4j.model.structureddecision.StructuredDecisionRequest;
import dev.langchain4j.model.structureddecision.StructuredDecisionRequestParameters;
import dev.langchain4j.model.structureddecision.StructuredDecisionResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SystemOneSupport {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;

    SystemOneSupport(HttpClient httpClient, String baseUrl, String apiKey) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    StructuredDecisionResponse decide(StructuredDecisionRequest request, StructuredDecisionRequestParameters parameters) {
        return decode(execute("/v1/systemone", payload(request, parameters)), request);
    }

    Map<String, Object> payload(StructuredDecisionRequest request, StructuredDecisionRequestParameters parameters) {
        return payload(request, parameters, false, false);
    }

    Map<String, Object> payload(StructuredDecisionRequest request, StructuredDecisionRequestParameters parameters,
                                boolean allowImages, boolean allowSpans) {
        if (!allowImages && !request.contents().isEmpty()) {
            throw new UnsupportedFeatureException("System One JSON does not support content attachments");
        }
        if (!parameters.additionalProperties().isEmpty()) {
            throw new UnsupportedFeatureException("Unsupported System One request parameters: "
                    + parameters.additionalProperties().keySet());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("state", request.state());
        if (parameters.modelName() != null) {
            payload.put("model", parameters.modelName());
        }
        payload.put("questions", mapQuestions(request.questions(), allowSpans));
        return payload;
    }

    SuccessfulHttpResponse execute(String path, Object payload) {
        HttpRequest.Builder httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, path)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")
                .body(toJson(payload));
        if (apiKey != null) {
            httpRequest.addHeader("Authorization", "Bearer " + apiKey);
        }
        return httpClient.execute(httpRequest.build());
    }

    SuccessfulHttpResponse executeMultipart(Object payload, List<SystemOneImages.InlineImage> images) {
        HttpRequest.Builder httpRequest = HttpRequest.builder()
                .method(POST)
                .url(baseUrl, "/v1/systemone")
                .addHeader("Accept", "application/json")
                .addFormDataField("request", toJson(payload));
        for (int i = 0; i < images.size(); i++) {
            SystemOneImages.InlineImage image = images.get(i);
            String name = "image" + i;
            httpRequest.addFormDataFile(name, name + "." + image.extension(), image.mimeType(), image.bytes());
        }
        if (apiKey != null) {
            httpRequest.addHeader("Authorization", "Bearer " + apiKey);
        }
        return httpClient.execute(httpRequest.build());
    }

    private static Map<String, Object> mapQuestions(Map<String, Question> questions, boolean allowSpans) {
        Map<String, Object> result = new LinkedHashMap<>();
        questions.forEach((name, question) -> result.put(name, mapQuestion(question, allowSpans)));
        return result;
    }

    private static Map<String, Object> mapQuestion(Question question, boolean allowSpans) {
        Map<String, Object> result = new LinkedHashMap<>();
        Object criteria = null;
        if (question instanceof NoulQuestion noul) {
            result.put("type", "noul");
            if (noul.criteria() != null) {
                criteria = Map.of("true", describe(noul.criteria()));
            }
        } else if (question instanceof ChoiceQuestion choice) {
            result.put("type", "choice");
            Map<String, Object> options = new LinkedHashMap<>();
            choice.options().forEach((name, value) -> options.put(name, describe(value)));
            criteria = options;
        } else if (question instanceof ScoreQuestion score) {
            result.put("type", "score");
            List<Object> levels = new ArrayList<>();
            score.levels().forEach((name, value) -> {
                Map<String, Object> level = new LinkedHashMap<>();
                level.put("level", name);
                level.putAll(describe(value));
                levels.add(level);
            });
            criteria = levels;
        } else if (allowSpans && question instanceof SpanQuestion span) {
            result.put("type", "span");
            if (span.maxTokens() != null) criteria = Map.of("max_tokens", span.maxTokens());
        } else if (allowSpans && question instanceof SpansQuestion spans) {
            result.put("type", "spans");
            Map<String, Object> limits = new LinkedHashMap<>();
            if (spans.maxTokens() != null) limits.put("max_tokens", spans.maxTokens());
            if (spans.maxItems() != null) limits.put("max_items", spans.maxItems());
            if (!limits.isEmpty()) criteria = limits;
        } else {
            throw new UnsupportedFeatureException("Unsupported System One question: " + question.getClass().getName());
        }
        result.put("instructions", question.instructions());
        if (criteria != null) {
            result.put("criteria", criteria);
        }
        return result;
    }

    private static Map<String, Object> describe(NoulCriteria criteria) {
        return describe(criteria.what(), criteria.notFor(), criteria.examples());
    }

    private static Map<String, Object> describe(OptionCriteria criteria) {
        return describe(criteria.what(), criteria.notFor(), criteria.examples());
    }

    private static Map<String, Object> describe(String what, String notFor, List<String> examples) {
        Map<String, Object> description = new LinkedHashMap<>();
        if (what != null) description.put("what", what);
        if (notFor != null) description.put("not_for", notFor);
        if (!examples.isEmpty()) description.put("examples", examples);
        return description;
    }

    @SuppressWarnings("unchecked")
    static StructuredDecisionResponse decode(SuccessfulHttpResponse response, StructuredDecisionRequest request) {
        Object body = fromJson(response.body(), Map.class);
        if (!(body instanceof Map<?, ?> json) || !(json.get("answers") instanceof Map<?, ?> answers)) {
            throw new IllegalArgumentException("System One response must contain answers");
        }
        StructuredDecisionResponse.Builder result = StructuredDecisionResponse.builder();
        Map<String, Object> metadata = new LinkedHashMap<>();
        json.forEach((key, value) -> {
            if (!"answers".equals(key)) metadata.put((String) key, value);
        });
        result.metadata(metadata);
        answers.forEach((name, value) -> {
            Question question = request.questions().get(name);
            if (question == null) {
                throw new IllegalArgumentException("Unexpected System One answer name: " + name);
            }
            if (!(value instanceof Map<?, ?> answer)) {
                throw new IllegalArgumentException("System One answer " + name + " must be an object");
            }
            result.answer((String) name, mapAnswer((Map<String, Object>) answer, question, (String) name));
        });
        return result.build();
    }

    private static StructuredDecisionAnswer mapAnswer(Map<String, Object> answer, Question question, String name) {
        if (answer == null) throw new IllegalArgumentException("System One answer is null");
        Object type = answer.get("type");
        if (type == null && question instanceof NoulQuestion) type = "noul";
        if (type == null && question instanceof ChoiceQuestion) type = "choice";
        if (type == null && question instanceof ScoreQuestion) type = "score";
        if (type == null && question instanceof SpanQuestion) type = "span";
        if (type == null && question instanceof SpansQuestion) type = "spans";
        String expected = question instanceof NoulQuestion ? "noul"
                : question instanceof ChoiceQuestion ? "choice"
                : question instanceof ScoreQuestion ? "score"
                : question instanceof SpanQuestion ? "span"
                : question instanceof SpansQuestion ? "spans" : null;
        if (expected == null || !expected.equals(type)) {
            throw new IllegalArgumentException("Unexpected System One answer type for " + name + ": " + type);
        }
        Double confidence = number(answer.get("confidence"), "confidence");
        ConfidenceProvenance provenance = confidence == null ? null : ConfidenceProvenance.PROVIDER_REPORTED;
        Map<String, Object> metadata = new LinkedHashMap<>(answer);
        metadata.keySet().removeAll(List.of("type", "noul", "choice", "score", "confidence"));
        if ("noul".equals(type)) {
            return new NoulAnswer(number(answer.get("noul"), "noul"), confidence, provenance, metadata);
        }
        if ("choice".equals(type)) {
            return new ChoiceAnswer((String) answer.get("choice"), confidence, provenance, metadata);
        }
        if ("score".equals(type)) {
            return new ScoreAnswer(number(answer.get("score"), "score"), confidence, provenance, metadata);
        }
        if ("span".equals(type)) {
            metadata.keySet().removeAll(List.of("found", "text", "start", "end"));
            SpanAnswer.SpanValue value = spanValue(answer, true);
            return new SpanAnswer(value, confidence, provenance, metadata);
        }
        if ("spans".equals(type)) {
            metadata.keySet().removeAll(List.of("found", "items"));
            List<SpanAnswer.SpanValue> items = new ArrayList<>();
            Object rawItems = answer.get("items");
            if (!(rawItems instanceof List<?> list)) {
                throw new IllegalArgumentException("System One spans answer must contain items");
            }
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> span)) {
                    throw new IllegalArgumentException("System One spans item must be an object");
                }
                items.add(spanValue((Map<String, Object>) span, false));
            }
            return new SpansAnswer(new SpansAnswer.SpansValue(requiredBoolean(answer.get("found"), "found"), items),
                    confidence, provenance, metadata);
        }
        throw new IllegalArgumentException("Unsupported System One answer type: " + type);
    }

    private static SpanAnswer.SpanValue spanValue(Map<String, Object> answer, boolean requireFound) {
        boolean found = requireFound ? requiredBoolean(answer.get("found"), "found") : true;
        Object text = answer.get("text");
        if (found && !(text instanceof String)) {
            throw new IllegalArgumentException("Found span must contain text");
        }
        Integer start = integer(answer.get("start"));
        Integer end = integer(answer.get("end"));
        if (found && (start == null || end == null || start < 0 || end < start)) {
            throw new IllegalArgumentException("Found span must contain valid offsets");
        }
        Map<String, Object> diagnostics = new LinkedHashMap<>(answer);
        diagnostics.keySet().removeAll(List.of("type", "found", "text", "start", "end", "confidence"));
        return new SpanAnswer.SpanValue(found, (String) text, start, end,
                number(answer.get("confidence"), "confidence"), diagnostics);
    }

    private static boolean requiredBoolean(Object value, String name) {
        if (value instanceof Boolean flag) return flag;
        throw new IllegalArgumentException("System One " + name + " must be a boolean");
    }

    private static Integer integer(Object value) {
        if (value == null) return null;
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != Math.rint(number.doubleValue())
                || number.doubleValue() < Integer.MIN_VALUE || number.doubleValue() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("System One span offset must be an integer");
        }
        return number.intValue();
    }

    private static Double number(Object value, String name) {
        if (value == null) return null;
        if (value instanceof Number number) return number.doubleValue();
        throw new IllegalArgumentException("System One " + name + " must be a number");
    }
}
